/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.export;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import fiji.util.gui.GenericDialogPlus;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.VirtualStack;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.TiffEncoder;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.io.ReadXMLValue;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Save3dTIFF implements ImgExport, Calibrateable
{
	public static boolean defaultUseXMLPath = true;
	public static String defaultPath = null;
	public static String defaultFN = "";

	String path, fnAddition = defaultFN;
	boolean compress;

	String unit = "px";
	double cal = 1.0;

	public Save3dTIFF( final String path ) { this( path, false ); }
	public Save3dTIFF( final String path, final boolean compress )
	{
		this.path = path;
		this.compress = compress;
	}

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title )
	{
		exportImage( img, null, Double.NaN, Double.NaN, title, null );
	}

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title, final String xml_path, final Integer index )
	{
		exportImage( img, null, Double.NaN, Double.NaN, title, null, xml_path, index );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup )
	{
		return exportImage( img, bb, downsampling, anisoF, title, fusionGroup, Double.NaN, Double.NaN );
	}

	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final String xml_path,
			final Integer index )
	{
		return exportImage( img, bb, downsampling, anisoF, title, fusionGroup, Double.NaN, Double.NaN, xml_path, index );
	}


	public String getFileName( final String title )
	{
		String fileName;
		String add;

		if ( fnAddition.length() > 0 )
			add = fnAddition + "_";
		else
			add = "";

		if ( !title.endsWith( ".tif" ) )
			fileName = new File( path, add + title + ".tif" ).getAbsolutePath();
		else
			fileName = new File( path, add + title ).getAbsolutePath();

		if ( compress )
			return fileName + ".zip";
		else
			return fileName;
	}

	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;


		// determine min and max
		final double[] minmax = DisplayImage.getFusionMinMax( img, min, max );

		final ImagePlus imp = DisplayImage.getImagePlusInstance( img, true, title, minmax[ 0 ], minmax[ 1 ] );

		DisplayImage.setCalibration( imp, bb, downsampling, anisoF, cal, unit );

		imp.updateAndDraw();

		final String fileName = getFileName( title );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving file " + fileName );

		final boolean success;

		if ( compress )
			success = new FileSaver( imp ).saveAsZip( fileName );
		else
			success = saveTiffStack( imp, fileName ); //new FileSaver( imp ).saveAsTiffStack( fileName );

		if ( success )
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved file " + fileName );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED saving file " + fileName );

		return success;
	}

	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max,
			final String xml_path,
			final Integer index )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;

		Calibration imp_cal = new Calibration();

		final ReadXMLValue readXML = new ReadXMLValue( xml_path );
		String unit = readXML.getValue( "unit", index );
		String pixel_cal = readXML.getValue( "size", index );

		String[] pixels_cal = pixel_cal.split(" ");
		float[] numbers = new float[pixels_cal.length];
		for (int i = 0; i < pixels_cal.length; ++i) {
			float number = Float.parseFloat(pixels_cal[i]);
			numbers[i] = number;
		}

		imp_cal.setUnit(unit);
		imp_cal.pixelWidth = numbers[0];
		imp_cal.pixelHeight = numbers[1];
		imp_cal.pixelDepth = numbers[2];

		cal = numbers[0];

		// determine min and max
		final double[] minmax = DisplayImage.getFusionMinMax( img, min, max );

		final ImagePlus imp = DisplayImage.getImagePlusInstance( img, true, title, minmax[ 0 ], minmax[ 1 ] );
		imp.setCalibration(imp_cal);

		DisplayImage.setCalibration( imp, bb, downsampling, anisoF, cal, unit );

		imp.updateAndDraw();

		final String fileName = getFileName( title );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving file " + fileName );

		final boolean success;

		if ( compress )
			success = new FileSaver( imp ).saveAsZip( fileName );
		else
			success = saveTiffStack( imp, fileName ); //new FileSaver( imp ).saveAsTiffStack( fileName );

		if ( success )
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved file " + fileName );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED saving file " + fileName );

		return success;
	}

	/*
	 * Reimplementation from ImageJ FileSaver class. Necessary since it traverses the entire virtual stack once to collect some
	 * slice labels, which takes forever in this case.
	 */
	public static boolean saveTiffStack( final ImagePlus imp, final String path )
	{
		FileInfo fi = imp.getFileInfo();

		boolean virtualStack = imp.getStack().isVirtual();
		if (virtualStack)
			fi.virtualStack = (VirtualStack)imp.getStack();
		fi.info = imp.getInfoProperty();
		fi.description = new FileSaver( imp ).getDescriptionString();
		DataOutputStream out = null;
		try {
			TiffEncoder file = new TiffEncoder(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		} catch (IOException e) {
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": ERROR: Cannot save file '"+ path + "':" + e );
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return true;
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Save fused images as 3D TIFF" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();

			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );

			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );
		}

		PluginHelper.addSaveAsDirectoryField( gd, "Output_file_directory", defaultPath, 80 );
		gd.addStringField( "Filename_addition", defaultFN );
		gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", Resave_TIFF.defaultCompress );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.path = defaultPath = gd.getNextString().trim();
		this.fnAddition = defaultFN = gd.getNextString().trim();
		this.compress = Resave_TIFF.defaultCompress = gd.getNextBoolean();

		return true;
	}

	@Override
	public ImgExport newInstance() { return new Save3dTIFF( path ); }

	@Override
	public String getDescription() { return "Save as (compressed) TIFF stacks"; }

	@Override
	public boolean finish()
	{
		// nothing to do
		return false;
	}

	@Override
	public void setCalibration( final double pixelSize, final String unit )
	{
		this.cal = pixelSize;
		this.unit = unit;
	}

	@Override
	public String getUnit() { return unit; }

	@Override
	public double getPixelSize() { return cal; }
}
