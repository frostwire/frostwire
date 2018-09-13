/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.tables.*;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class IPFilterTableMediator extends AbstractTableMediator<IPFilterTableMediator.IPFilterModel, IPFilterTableMediator.IPFilterDataLine, IPFilterTableMediator.IPRange> {

    private static IPFilterTableMediator INSTANCE = null;

    public static IPFilterTableMediator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IPFilterTableMediator();
        }
        if (INSTANCE == null) {
            throw new RuntimeException("Check your logic, IPFilterTableMediator instance being nullified by some thread");
        }
        return INSTANCE;
    }

    // AbstractTableMediator instances are meant to be singleton, otherwise issues with duplicate settings are raised upon the
    // second instantiations.
    private IPFilterTableMediator() {
        super("IP_FILTER_TABLE_MEDIATOR_ID");
    }

    @Override
    protected void updateSplashScreen() {

    }

    @Override
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new IPFilterTableMediator.IPFilterModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    }

    @Override
    protected JPopupMenu createPopupMenu() {
        return null;
    }

    @Override
    public void handleActionKey() {

    }

    @Override
    public void handleSelection(int row) {

    }

    @Override
    public void handleNoSelection() {

    }

    class IPFilterModel extends BasicDataLineModel<IPFilterDataLine, IPRange> {
        IPFilterModel() {
            super(IPFilterDataLine.class);
        }
    }

    public static class IPFilterDataLine extends AbstractDataLine<IPRange> {
        private final static int DESCRIPTION_ID = 0;
        private final static int START = 1;
        private final static int END = 2;
        private final static LimeTableColumn[] columns = new LimeTableColumn[]{
                new LimeTableColumn(DESCRIPTION_ID, "DESCRIPTION_ID", I18n.tr("Description"), 180, true, true, true, String.class),
                new LimeTableColumn(START, "START", I18n.tr("Start"), 180, true, true, true, String.class),
                new LimeTableColumn(END, "END", I18n.tr("End"), 180, true, true, true, String.class)};

        public IPFilterDataLine() {}

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public LimeTableColumn getColumn(int col) {
            return columns[col];
        }

        @Override
        public boolean isDynamic(int col) {
            return false;
        }

        @Override
        public boolean isClippable(int col) {
            return false;
        }

        @Override
        public Object getValueAt(int col) {
            IPRange ipRangeHolder = getInitializeObject();
            switch (col) {
                case DESCRIPTION_ID:
                    return ipRangeHolder.description();
                case START:
                    return ipRangeHolder.startAddress();
                case END:
                    return ipRangeHolder.endAddress();
                default:
                    return null;
            }
        }

        @Override
        public int getTypeAheadColumn() {
            return 0;
        }

    }

    public static class IPRange {

        private String description;
        private String startAddress;
        private String endAddress;

        IPRange(String description, String startAddress, String endAddress) {
            this.description = description;
            this.startAddress = startAddress;
            this.endAddress = endAddress;
        }

        public String description() {
            return description;
        }

        String startAddress() {
            return startAddress;
        }

        String endAddress() {
            return endAddress;
        }

        void writeObjectTo(OutputStream os) throws IOException {
            os.write(description.length());     // DESCRIPTION LENGTH
            os.write(description.getBytes(StandardCharsets.UTF_8)); // DESCRIPTION
            InetAddress bufferRange = InetAddress.getByName(startAddress);
            boolean isIPv4 = bufferRange instanceof Inet4Address;
            os.write(isIPv4 ? 4 : 6);           // START RANGE IP VERSION TYPE <4 | 6>
            os.write(bufferRange.getAddress()); // START RANGE IP <4 bytes (32bits)>|[ 16 bytes (128bits)]

            bufferRange = InetAddress.getByName(endAddress);
            isIPv4 = bufferRange instanceof Inet4Address;
            os.write(isIPv4 ? 4 : 6);           // END RANGE IP VERSION TYPE <4 | 6>
            os.write(bufferRange.getAddress()); // END RANGE IP <4 bytes (32bits)>|[ 16 bytes (128bits)]
            os.flush();
        }

        static IPRange readObjectFrom(InputStream is) throws IOException {
            int descriptionLength = is.read(); // DESCRIPTION LENGTH
            byte[] descBuffer = new byte[descriptionLength];
            is.read(descBuffer); // DESCRIPTION
            String description = new String(descBuffer, StandardCharsets.UTF_8);
            String startAddress = null;
            int ipVersionType = is.read(); // START RANGE IP VERSION TYPE <4 | 6>
            if (ipVersionType == 4) {
                byte[] address = new byte[4];
                is.read(address); // START RANGE IP <4 bytes (32bits)>
                startAddress = InetAddress.getByAddress(address).getHostAddress();
            } else if (ipVersionType == 6) {
                byte[] address = new byte[16];
                is.read(address); // START RANGE IP <16 bytes (128bits)>
                startAddress = InetAddress.getByAddress(address).getHostAddress();
            }
            String endAddress = null;
            ipVersionType = is.read(); // END RANGE IP VERSION TYPE <4 | 6>
            if (ipVersionType == 4) {
                byte[] address = new byte[4];
                is.read(address); // END RANGE IP <4 bytes (32bits)>
                endAddress = InetAddress.getByAddress(address).getHostAddress();
            } else if (ipVersionType == 6) {
                byte[] address = new byte[16];
                is.read(address); // END RANGE IP <16 bytes (128bits)>
                endAddress = InetAddress.getByAddress(address).getHostAddress();
            }
            return new IPRange(description, startAddress, endAddress);
        }

    }
}
