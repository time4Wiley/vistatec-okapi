/*===========================================================================
  Copyright (C) 2010 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

package net.sf.okapi.steps.imagemodification;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.IParameters;
import net.sf.okapi.common.UsingParameters;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiIOException;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.pipeline.annotations.StepParameterMapping;
import net.sf.okapi.common.pipeline.annotations.StepParameterType;
import net.sf.okapi.common.resource.RawDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UsingParameters(Parameters.class)
public class ImageModificationStep extends BasePipelineStep {

	private final Logger LOGGER = LoggerFactory.getLogger(getClass());

	private boolean isDone;
	private Parameters params;
	private URI outputURI;
	private String currentSuffix;
	private List<String> suffixes;

	public ImageModificationStep () {
		params = new Parameters();
	}
	
	@Override
	public String getName () {
		return "Image Modification";
	}

	@Override
	public String getDescription () {
		return "Create a modified copy of image files. "
			+ "Expects: image raw document. Sends back: image raw document.";
	}

	@Override
	public boolean isDone () {
		return isDone;
	}

	@Override
	public IParameters getParameters () {
		return this.params;
	}

	@Override
	public void setParameters (IParameters params) {
		this.params = (Parameters)params;
	}

	@StepParameterMapping(parameterType = StepParameterType.OUTPUT_URI)
	public void setOutputURI (URI outputURI) {
		this.outputURI = outputURI;
	}
	
	@Override
	protected Event handleStartBatchItem (Event event) {
		suffixes = new ArrayList<String>(Arrays.asList(ImageIO.getWriterFileSuffixes()));
		isDone = false;
		return event;
	}

	@Override
	public Event handleRawDocument (Event event) {
		RawDocument rd = event.getRawDocument();
		try {
			URI uri = rd.getInputURI();
			if ( uri != null ) {
				File inputFile = new File(uri);
				String newFormat = params.getFormat();

				// If needed: detect the format of the original file
				if ( newFormat.isEmpty() ) {
					String ext = Util.getExtension(inputFile.getAbsolutePath()).toLowerCase();
					if ( ext.length() > 1 ) ext = ext.substring(1); // Remove period
					if ( !ext.equalsIgnoreCase(currentSuffix) ) {
						if ( suffixes.contains(ext) ) {
							currentSuffix = ext;
							newFormat = currentSuffix;
						}
						else { // No such writer, fall back to "png"
							LOGGER.warn("No image writer available for '{}'. Using 'png' instead.", newFormat);
							currentSuffix = "png";
							newFormat = "png";
						}
					}
					else { // Same as before
						newFormat = currentSuffix;
					}
				}
				else {
					// Else: newFormat is set to the proper value
					// And we can change the extension of the output file
					//TODO: extension of output
				}

				// Convert
				BufferedImage imgOri = ImageIO.read(inputFile);
				BufferedImage imgTmp;
				BufferedImage imgOut;
				
				// Convert the size if requested
				if (( params.getScaleHeight() != 100 ) || ( params.getScaleWidth() != 100 )) {
					imgTmp = createResizedCopy(imgOri);
				}
				else {
					imgTmp = imgOri;
				}
				
				if ( params.getMakeGray() ) {
					imgOut = new BufferedImage(imgTmp.getWidth(), imgTmp.getHeight(), BufferedImage.TYPE_BYTE_GRAY);  
					Graphics g = imgOut.getGraphics();  
					g.drawImage(imgTmp, 0, 0, null);  
					g.dispose();  				
				}
				else {
					imgOut = imgTmp;
				}
				
				// Save the output
				File outFile;
				if ( isLastOutputStep() ) {
					outFile = new File(outputURI);
					Util.createDirectories(outFile.getAbsolutePath());
				}
				else {
					try {
						outFile = File.createTempFile("~okapi-46_okp-im_", ".tmp");
					}
					catch ( Throwable e ) {
						throw new OkapiIOException("Cannot create temporary output.", e);
					}
				}
			    ImageIO.write(imgOut, newFormat, outFile);
			}
			else {
				throw new OkapiIOException("Input type not supported (must be URI).");
			}
		}
		catch ( Throwable e ) {
			throw new OkapiIOException("Error while loading or modifying the image.", e);
		}
		finally {
			isDone = true;
		}		
		return event;
	}

	private BufferedImage createResizedCopy (Image originalImage) {
		Graphics2D g2d = null;
		try {
			// Compute the new size
			int oriWidth = originalImage.getWidth(null);
			int oriHeight = originalImage.getHeight(null);
			Double tmp = oriWidth * (params.getScaleWidth() / 100.0);
			int newWidth = (tmp.intValue() < 1 ? 1 : tmp.intValue());
			tmp = oriHeight * (params.getScaleHeight() / 100.0);
			int newHeight = (tmp.intValue() < 1 ? 1 : tmp.intValue());
			
			// Perform the resizing
			BufferedImage scaledImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB); 
			g2d = scaledImg.createGraphics();
			g2d.setComposite(AlphaComposite.Src); 
			g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
			return scaledImg;
		}
		finally {
			if ( g2d != null ) g2d.dispose(); 
		}
	}

}
