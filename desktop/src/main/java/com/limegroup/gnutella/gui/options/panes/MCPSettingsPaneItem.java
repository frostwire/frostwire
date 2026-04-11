package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.mcp.agents.AgentConfigWriter;
import com.frostwire.mcp.agents.AgentDetector;
import com.frostwire.mcp.agents.AgentInfo;
import com.frostwire.mcp.desktop.MCPStartupHook;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.MCPSettings;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MCPSettingsPaneItem extends AbstractPaneItem {

    private static final Logger LOG = Logger.getLogger(MCPSettingsPaneItem.class);

    private final static String TITLE = I18n.tr("MCP Server");
    private final static String LABEL = I18n.tr("Configure the FrostWire MCP (Model Context Protocol) server to allow AI agents to control FrostWire programmatically.");

    private final JCheckBox ENABLED_CHECKBOX = new JCheckBox(I18n.tr("Enable MCP Server"));
    private final JCheckBox AUTO_START_CHECKBOX = new JCheckBox(I18n.tr("Start MCP server when FrostWire starts"));
    private final JButton START_BUTTON = new JButton(I18n.tr("Start"));
    private final JButton STOP_BUTTON = new JButton(I18n.tr("Stop"));
    private final JButton RESTART_BUTTON = new JButton(I18n.tr("Restart"));
    private final JLabel STATUS_LABEL = new JLabel(I18n.tr("Stopped"));

    private final JTextField HOST_FIELD = new SizedTextField(15, SizePolicy.RESTRICT_HEIGHT);
    private final WholeNumberField PORT_FIELD = new SizedWholeNumberField(8796, 5, SizePolicy.RESTRICT_BOTH);
    private final JCheckBox TLS_CHECKBOX = new JCheckBox(I18n.tr("Enable HTTPS (TLS)"));

    private final AgentsTableModel agentsTableModel = new AgentsTableModel();
    private final JTable AGENTS_TABLE = new JTable(agentsTableModel);
    private final JButton REFRESH_BUTTON = new JButton(I18n.tr("Refresh"));

    public MCPSettingsPaneItem() {
        super(TITLE, LABEL);

        ENABLED_CHECKBOX.addItemListener(e -> updateState());
        add(ENABLED_CHECKBOX);
        add(AUTO_START_CHECKBOX);
        add(getHorizontalSeparator());

        BoxPanel controlPanel = new BoxPanel(BoxPanel.X_AXIS);
        controlPanel.add(START_BUTTON);
        controlPanel.addHorizontalComponentGap();
        controlPanel.add(STOP_BUTTON);
        controlPanel.addHorizontalComponentGap();
        controlPanel.add(RESTART_BUTTON);
        controlPanel.addHorizontalComponentGap();
        controlPanel.add(STATUS_LABEL);
        add(controlPanel);
        add(getHorizontalSeparator());

        BoxPanel networkPanel = new BoxPanel(BoxPanel.X_AXIS);
        LabeledComponent hostComp = new LabeledComponent(I18n.tr("Host:"),
                HOST_FIELD, LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        networkPanel.add(hostComp.getComponent());
        networkPanel.addHorizontalComponentGap();
        LabeledComponent portComp = new LabeledComponent(I18n.tr("Port:"),
                PORT_FIELD, LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        networkPanel.add(portComp.getComponent());
        add(networkPanel);
        add(TLS_CHECKBOX);

        JLabel tlsInfo = new JLabel("<html><i>" +
                I18n.tr("When HTTPS is enabled, a self-signed certificate is auto-generated for local use.") +
                "</i></html>");
        add(tlsInfo);
        add(getVerticalSeparator());
        add(getVerticalSeparator());

        JLabel clientsHeader = new JLabel("<html><b>" + I18n.tr("MCP Clients/Harnesses") + "</b></html>");
        add(clientsHeader);
        add(getVerticalSeparator());

        AGENTS_TABLE.setRowHeight(28);
        AGENTS_TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        AGENTS_TABLE.getColumnModel().getColumn(0).setPreferredWidth(150);
        AGENTS_TABLE.getColumnModel().getColumn(1).setPreferredWidth(120);
        AGENTS_TABLE.getColumnModel().getColumn(2).setPreferredWidth(200);
        AGENTS_TABLE.getColumnModel().getColumn(0).setCellRenderer(new PaddedRenderer());
        AGENTS_TABLE.getColumnModel().getColumn(1).setCellRenderer(new PaddedStatusRenderer());
        AGENTS_TABLE.getColumnModel().getColumn(2).setCellRenderer(new ActionsRenderer());
        AGENTS_TABLE.getColumnModel().getColumn(2).setCellEditor(new ActionsEditor());
        AGENTS_TABLE.setShowGrid(false);
        AGENTS_TABLE.setIntercellSpacing(new Dimension(0, 0));

        JScrollPane agentsScroll = new JScrollPane(AGENTS_TABLE);
        agentsScroll.setMaximumSize(new Dimension(9999, 200));
        agentsScroll.setPreferredSize(new Dimension(500, 200));
        add(agentsScroll);

        BoxPanel agentButtonsPanel = new BoxPanel(BoxPanel.X_AXIS);
        agentButtonsPanel.add(REFRESH_BUTTON);
        add(agentButtonsPanel);

        START_BUTTON.addActionListener(e -> startServer());
        STOP_BUTTON.addActionListener(e -> stopServer());
        RESTART_BUTTON.addActionListener(e -> restartServer());
        REFRESH_BUTTON.addActionListener(e -> refreshAgents());

        refreshAgents();
    }

    public void initOptions() {
        ENABLED_CHECKBOX.setSelected(MCPSettings.MCP_SERVER_ENABLED.getValue());
        AUTO_START_CHECKBOX.setSelected(MCPSettings.MCP_SERVER_AUTO_START.getValue());
        HOST_FIELD.setText(MCPSettings.MCP_SERVER_HOST.getValue());
        PORT_FIELD.setValue(MCPSettings.MCP_SERVER_PORT.getValue());
        TLS_CHECKBOX.setSelected(MCPSettings.MCP_SERVER_TLS_ENABLED.getValue());
        updateStatus();
        updateState();
    }

    public boolean applyOptions() {
        int port = PORT_FIELD.getValue();
        if (port < 1 || port > 65535) {
            GUIMediator.showError(I18n.tr("Port must be between 1 and 65535"));
            return false;
        }
        String host = HOST_FIELD.getText().trim();
        if (host.isEmpty()) {
            GUIMediator.showError(I18n.tr("Host cannot be empty"));
            return false;
        }

        boolean wasEnabled = MCPSettings.MCP_SERVER_ENABLED.getValue();
        boolean nowEnabled = ENABLED_CHECKBOX.isSelected();

        MCPSettings.MCP_SERVER_ENABLED.setValue(nowEnabled);
        MCPSettings.MCP_SERVER_AUTO_START.setValue(AUTO_START_CHECKBOX.isSelected());
        MCPSettings.MCP_SERVER_HOST.setValue(host);
        MCPSettings.MCP_SERVER_PORT.setValue(port);
        MCPSettings.MCP_SERVER_TLS_ENABLED.setValue(TLS_CHECKBOX.isSelected());

        if (nowEnabled && !wasEnabled) {
            startServer();
        } else if (!nowEnabled && wasEnabled) {
            stopServer();
        } else if (nowEnabled) {
            restartServer();
        }

        return true;
    }

    public boolean isDirty() {
        if (MCPSettings.MCP_SERVER_ENABLED.getValue() != ENABLED_CHECKBOX.isSelected()) return true;
        if (MCPSettings.MCP_SERVER_AUTO_START.getValue() != AUTO_START_CHECKBOX.isSelected()) return true;
        if (!MCPSettings.MCP_SERVER_HOST.getValue().equals(HOST_FIELD.getText().trim())) return true;
        if (MCPSettings.MCP_SERVER_PORT.getValue() != PORT_FIELD.getValue()) return true;
        if (MCPSettings.MCP_SERVER_TLS_ENABLED.getValue() != TLS_CHECKBOX.isSelected()) return true;
        return false;
    }

    private void updateState() {
        boolean enabled = ENABLED_CHECKBOX.isSelected();
        HOST_FIELD.setEnabled(enabled);
        PORT_FIELD.setEnabled(enabled);
        TLS_CHECKBOX.setEnabled(enabled);
        AUTO_START_CHECKBOX.setEnabled(enabled);
        START_BUTTON.setEnabled(enabled && !MCPStartupHook.isRunning());
        STOP_BUTTON.setEnabled(enabled && MCPStartupHook.isRunning());
        RESTART_BUTTON.setEnabled(enabled && MCPStartupHook.isRunning());
    }

    private void updateStatus() {
        if (MCPStartupHook.isRunning()) {
            STATUS_LABEL.setText(I18n.tr("Running on ") + MCPStartupHook.getServerUrl());
            STATUS_LABEL.setForeground(new Color(0, 128, 0));
        } else {
            STATUS_LABEL.setText(I18n.tr("Stopped"));
            STATUS_LABEL.setForeground(Color.RED);
        }
        updateState();
    }

    private void startServer() {
        applySettings();
        String url = getMcpUrl();
        LOG.info("Start button pressed, starting MCP server at " + url);
        START_BUTTON.setEnabled(false);
        STATUS_LABEL.setText(I18n.tr("Starting..."));
        STATUS_LABEL.setForeground(Color.DARK_GRAY);
        CompletableFuture.runAsync(() -> {
            try {
                MCPStartupHook.startServer();
                LOG.info("MCP server start completed, isRunning=" + MCPStartupHook.isRunning());
                GUIMediator.safeInvokeLater(this::updateStatus);
            } catch (Exception e) {
                LOG.error("MCP server start failed: " + e.getMessage(), e);
                GUIMediator.safeInvokeLater(() -> {
                    GUIMediator.showError(I18n.tr("Failed to start MCP server: ") + e.getMessage());
                    updateStatus();
                });
            }
        });
    }

    private void stopServer() {
        LOG.info("Stop button pressed, stopping MCP server");
        STOP_BUTTON.setEnabled(false);
        STATUS_LABEL.setText(I18n.tr("Stopping..."));
        STATUS_LABEL.setForeground(Color.DARK_GRAY);
        CompletableFuture.runAsync(() -> {
            try {
                MCPStartupHook.stopServer();
                LOG.info("MCP server stopped, isRunning=" + MCPStartupHook.isRunning());
            } catch (Exception e) {
                LOG.error("MCP server stop failed: " + e.getMessage(), e);
            }
            GUIMediator.safeInvokeLater(this::updateStatus);
        });
    }

    private void restartServer() {
        applySettings();
        String url = getMcpUrl();
        LOG.info("Restart button pressed, restarting MCP server at " + url);
        RESTART_BUTTON.setEnabled(false);
        STATUS_LABEL.setText(I18n.tr("Restarting..."));
        STATUS_LABEL.setForeground(Color.DARK_GRAY);
        CompletableFuture.runAsync(() -> {
            try {
                MCPStartupHook.restartServer();
                LOG.info("MCP server restart completed, isRunning=" + MCPStartupHook.isRunning());
                GUIMediator.safeInvokeLater(this::updateStatus);
            } catch (Exception e) {
                LOG.error("MCP server restart failed: " + e.getMessage(), e);
                GUIMediator.safeInvokeLater(() -> {
                    GUIMediator.showError(I18n.tr("Failed to restart MCP server: ") + e.getMessage());
                    updateStatus();
                });
            }
        });
    }

    private void applySettings() {
        MCPSettings.MCP_SERVER_HOST.setValue(HOST_FIELD.getText().trim());
        MCPSettings.MCP_SERVER_PORT.setValue(PORT_FIELD.getValue());
        MCPSettings.MCP_SERVER_TLS_ENABLED.setValue(TLS_CHECKBOX.isSelected());
    }

    private void refreshAgents() {
        CompletableFuture.supplyAsync((Supplier<List<AgentInfo>>) AgentDetector::detectAll)
                .thenAcceptAsync(agents -> GUIMediator.safeInvokeLater(() -> agentsTableModel.setAgents(agents)));
    }

    private void configureAgent(AgentInfo agent) {
        String mcpUrl = getMcpUrl();
        CompletableFuture.runAsync(() -> {
            AgentConfigWriter.configure(agent, mcpUrl);
            GUIMediator.safeInvokeLater(this::refreshAgents);
        });
    }

    private void removeAgent(AgentInfo agent) {
        CompletableFuture.runAsync(() -> {
            AgentConfigWriter.deconfigure(agent);
            GUIMediator.safeInvokeLater(this::refreshAgents);
        });
    }

    private String getMcpUrl() {
        String scheme = TLS_CHECKBOX.isSelected() ? "https" : "http";
        return scheme + "://" + HOST_FIELD.getText().trim() + ":" + PORT_FIELD.getValue() + "/mcp";
    }

    private class AgentsTableModel extends AbstractTableModel {
        private final String[] COLUMN_NAMES = {I18n.tr("Client"), I18n.tr("Status"), I18n.tr("Actions")};
        private List<AgentInfo> agents = new ArrayList<>();

        void setAgents(List<AgentInfo> agents) {
            this.agents = new ArrayList<>(agents);
            this.agents.sort(Comparator.comparing(AgentInfo::getName, String.CASE_INSENSITIVE_ORDER));
            fireTableDataChanged();
        }

        AgentInfo getAgent(int row) {
            return agents.get(row);
        }

        @Override
        public int getRowCount() {
            return agents.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AgentInfo agent = agents.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return agent.getName();
                case 1:
                    if (agent.isInstalled()) {
                        return agent.isConfigured() ? I18n.tr("Configured") : I18n.tr("Not configured");
                    }
                    return I18n.tr("Not installed");
                case 2:
                    return agent;
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2;
        }
    }

    private class PaddedRenderer extends DefaultTableCellRenderer {
        private final Border BORDER = BorderFactory.createEmptyBorder(0, 5, 0, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(BORDER);
            return this;
        }
    }

    private class PaddedStatusRenderer extends DefaultTableCellRenderer {
        private final Border BORDER = BorderFactory.createEmptyBorder(0, 5, 0, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(BORDER);
            AgentInfo agent = agentsTableModel.getAgent(row);
            if (agent.isInstalled()) {
                setForeground(agent.isConfigured() ? new Color(0, 128, 0) : Color.ORANGE);
            } else {
                setForeground(Color.GRAY);
            }
            return this;
        }
    }

    private class ActionsRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        private final JButton configureBtn = new JButton(I18n.tr("Configure"));
        private final JButton removeBtn = new JButton(I18n.tr("Remove"));

        ActionsRenderer() {
            configureBtn.setMargin(new Insets(0, 4, 0, 4));
            configureBtn.setFocusable(false);
            removeBtn.setMargin(new Insets(0, 4, 0, 4));
            removeBtn.setFocusable(false);
            panel.setOpaque(true);
            panel.add(configureBtn);
            panel.add(removeBtn);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            AgentInfo agent = agentsTableModel.getAgent(row);
            configureBtn.setEnabled(agent.isInstalled() && !agent.isConfigured());
            removeBtn.setEnabled(agent.isConfigured());
            if (isSelected) {
                panel.setBackground(table.getSelectionBackground());
            } else {
                panel.setBackground(table.getBackground());
            }
            return panel;
        }
    }

    private class ActionsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        private final JButton configureBtn = new JButton(I18n.tr("Configure"));
        private final JButton removeBtn = new JButton(I18n.tr("Remove"));

        ActionsEditor() {
            configureBtn.setMargin(new Insets(0, 4, 0, 4));
            configureBtn.setFocusable(false);
            removeBtn.setMargin(new Insets(0, 4, 0, 4));
            removeBtn.setFocusable(false);
            panel.add(configureBtn);
            panel.add(removeBtn);

            configureBtn.addActionListener(e -> {
                int row = AGENTS_TABLE.getEditingRow();
                if (row >= 0) {
                    AgentInfo agent = agentsTableModel.getAgent(row);
                    configureAgent(agent);
                    fireEditingStopped();
                }
            });

            removeBtn.addActionListener(e -> {
                int row = AGENTS_TABLE.getEditingRow();
                if (row >= 0) {
                    AgentInfo agent = agentsTableModel.getAgent(row);
                    removeAgent(agent);
                    fireEditingStopped();
                }
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            AgentInfo agent = agentsTableModel.getAgent(row);
            configureBtn.setEnabled(agent.isInstalled() && !agent.isConfigured());
            removeBtn.setEnabled(agent.isConfigured());
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
    }
}
