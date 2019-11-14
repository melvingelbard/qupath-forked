package qupath.lib.images.writers.ome;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import loci.common.ByteArrayHandle;
import loci.common.IRandomAccess;
import loci.common.Location;
import loci.formats.FormatException;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ome.OMEPyramidWriter.CompressionType;
import qupath.lib.regions.RegionRequest;

/**
 * {@link ImageWriter} for writing OME-TIFF images. For greater control, see {@link OMEPyramidWriter}.
 * 
 * @author Pete Bankhead
 */
public class OMETiffWriter implements ImageWriter<BufferedImage> {

	@Override
	public String getName() {
		return "OME TIFF";
	}

	@Override
	public Collection<String> getExtensions() {
		return Arrays.asList("ome.tif", "ome.tiff");
	}

	@Override
	public boolean supportsT() {
		return true;
	}

	@Override
	public boolean supportsZ() {
		return true;
	}

	@Override
	public boolean supportsRGB() {
		return true;
	}

	@Override
	public boolean suportsImageType(ImageServer<BufferedImage> server) {
		return true;
	}

	@Override
	public boolean supportsPyramidal() {
		return true;
	}

	@Override
	public boolean supportsPixelSize() {
		return true;
	}

	@Override
	public String getDetails() {
		return "Write image as an OME-TIFF image using Bio-Formats. Format is flexible, preserving most image metadata.";
	}

	@Override
	public Class<BufferedImage> getImageClass() {
		return BufferedImage.class;
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, String pathOutput)
			throws IOException {
		try {
			OMEPyramidWriter.writeImage(server, pathOutput, CompressionType.DEFAULT, region);
		} catch (FormatException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void writeImage(BufferedImage img, String pathOutput) throws IOException {
		writeImage(
				new WrappedBufferedImageServer(pathOutput, img),
				RegionRequest.createInstance(pathOutput, 1.0, 0, 0, img.getWidth(), img.getHeight(), 0, 0),
				pathOutput);
	}
	
	@Override
	public void writeImage(ImageServer<BufferedImage> server, String pathOutput) throws IOException {
		try {
			OMEPyramidWriter.writeImage(server, pathOutput, CompressionType.DEFAULT);
		} catch (FormatException e) {
			throw new IOException(e);
		}
	}
	
	private String createInMemoryID(IRandomAccess access) {
		String id = UUID.randomUUID().toString() + "." + getDefaultExtension();
		Location.mapFile(id, access);
		return id;
	}

	/**
	 * Write OME-TIFF image to an output stream. Note that this must be able to write the image in-memory first, 
	 * and therefore is not suitable for very large images.
	 */
	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, OutputStream stream)
			throws IOException {
		var bytes = new ByteArrayHandle();
		String id = createInMemoryID(bytes);
		writeImage(server, region, id);
		stream.write(bytes.getBytes());
	}

	@Override
	public void writeImage(BufferedImage img, OutputStream stream) throws IOException {
		var bytes = new ByteArrayHandle();
		String id = createInMemoryID(bytes);
		writeImage(img, id);
		stream.write(bytes.getBytes());
	}

	/**
	 * Write OME-TIFF image to an output stream. Note that this must be able to write the image in-memory first, 
	 * and therefore is not suitable for very large images.
	 */
	@Override
	public void writeImage(ImageServer<BufferedImage> server, OutputStream stream) throws IOException {
		var bytes = new ByteArrayHandle();
		String id = createInMemoryID(bytes);
		writeImage(server, id);
		stream.write(bytes.getBytes());
	}

}
