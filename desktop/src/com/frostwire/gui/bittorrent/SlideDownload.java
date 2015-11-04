/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.frostwire.transfers.TransferState;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.limewire.util.SystemUtils;

import com.frostwire.gui.components.slides.Slide;
import com.frostwire.logging.Logger;
import com.frostwire.gui.DigestUtils;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;

public class SlideDownload extends HttpDownload {
	
	private static final Logger LOG = Logger.getLogger(SlideDownload.class);
    private final Slide slide;
    
    public SlideDownload(Slide slide) {
        super(slide.httpDownloadURL, slide.title, slide.saveFileAs, slide.size, slide.md5, true, true);
        this.slide = slide;
    }
    

    @Override
    protected void onComplete() {
        //TODO: unzip, delete zip
        
        if (slide.hasFlag(Slide.POST_DOWNLOAD_EXECUTE)) {
            if (verifySignature(getSaveLocation(), slide.httpDownloadURL)) {
                executeSlide(slide);
            } else {
            	state = TransferState.ERROR_SIGNATURE;
            }
        }
    }

    private void executeSlide(Slide slide) {
        List<String> command = new ArrayList<String>();
        command.add(getSaveLocation().getAbsolutePath());
        
        if (slide.executeParameters != null) {
            command.addAll(Arrays.asList(slide.executeParameters.split(" ")));
        }
        
        BufferedReader br = null;
        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            Process p = pb.start();
            
            //consume all output to avoid deadlock in some verisons of windows
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            
            while (br.readLine() != null) {
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(br);
        }
    }
    
    

    private boolean verifySignature(File saveLocation, String executableDownloadURL) {
    	String certificateURL = getCertificateURL(executableDownloadURL);
    	HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
    	
    	try {
    		String certificateInBase64 = httpClient.get(certificateURL);
    		certificateInBase64 = certificateInBase64.replace("-----BEGIN CERTIFICATE-----\r\n","").replace("-----END CERTIFICATE-----\r\n", "");
    		byte[] decodedCertificate = Base64.decodeBase64(certificateInBase64);
    		return SystemUtils.verifyExecutableSignature(saveLocation.getAbsolutePath(), decodedCertificate);
    	} catch (Exception e) {
    		LOG.error("Could not verify executable signature:\n" + e.getMessage(), e);
    		return false;
    	}
    }
    
    private String getCertificateURL(String url) {
        String urlMD5 = DigestUtils.getMD5(url);
        if (urlMD5 != null) {
        	//return "http://certs.frostwire.com/"+urlMD5;
        	return "http://s3.amazonaws.com/certs.frostwire.com/"+urlMD5;
        } else {
        	return null;
        }
    }
}
