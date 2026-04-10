package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.mcp.agents.AgentConfigWriter;
import com.frostwire.mcp.agents.AgentDetector;
import com.frostwire.mcp.agents.AgentInfo;
import com.frostwire.mcp.desktop.MCPStartupHook;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.MCPSettings;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MCPSettingsPaneItem extends AbstractPaneItem {

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

    private final JPanel AGENTS_PANEL = new JPanel();
    private final JButton CONFIGURE_ALL_BUTTON = new JButton(I18n.tr("Configure All"));
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
        add(getHorizontalSeparator());

        JLabel agentsLabel = new JLabel(I18n.tr("Detect and configure MCP clients:"));
        add(agentsLabel);

        AGENTS_PANEL.setLayout(new BoxLayout(AGENTS_PANEL, BoxLayout.Y_AXIS));
        JScrollPane agentsScroll = new JScrollPane(AGENTS_PANEL);
        agentsScroll.setMaximumSize(new Dimension(415, 150));
        agentsScroll.setPreferredSize(new Dimension(415, 150));
        add(agentsScroll);

        BoxPanel agentButtonsPanel = new BoxPanel(BoxPanel.X_AXIS);
        agentButtonsPanel.add(CONFIGURE_ALL_BUTTON);
        agentButtonsPanel.addHorizontalComponentGap();
        agentButtonsPanel.add(REFRESH_BUTTON);
        add(agentButtonsPanel);

        START_BUTTON.addActionListener(e -> startServer());
        STOP_BUTTON.addActionListener(e -> stopServer());
        RESTART_BUTTON.addActionListener(e -> restartServer());
        CONFIGURE_ALL_BUTTON.addActionListener(e -> configureAllAgents());
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
        CompletableFuture.runAsync(() -> {
            try {
                MCPStartupHook.startServer();
                GUIMediator.safeInvokeLater(this::updateStatus);
            } catch (Exception e) {
                GUIMediator.safeInvokeLater(() -> {
                    GUIMediator.showError(I18n.tr("Failed to start MCP server: ") + e.getMessage());
                    updateStatus();
                });
            }
        });
    }

    private void stopServer() {
        CompletableFuture.runAsync(() -> {
            try {
                MCPStartupHook.stopServer();
                GUIMediator.safeInvokeLater(this::updateStatus);
            } catch (Exception e) {
                GUIMediator.safeInvokeLater(this::updateStatus);
            }
        });
    }

    private void restartServer() {
        CompletableFuture.runAsync(() -> {
            try {
                MCPStartupHook.restartServer();
                GUIMediator.safeInvokeLater(this::updateStatus);
            } catch (Exception e) {
                GUIMediator.safeInvokeLater(() -> {
                    GUIMediator.showError(I18n.tr("Failed to restart MCP server: ") + e.getMessage());
                    updateStatus();
                });
            }
        });
    }

    private void refreshAgents() {
        CompletableFuture.supplyAsync((Supplier<List<AgentInfo>>) AgentDetector::detectAll)
                .thenAcceptAsync(agents -> GUIMediator.safeInvokeLater(() -> populateAgentsPanel(agents)));
    }

    private void populateAgentsPanel(List<AgentInfo> agents) {
        AGENTS_PANEL.removeAll();
        for (AgentInfo agent : agents) {
            BoxPanel row = new BoxPanel(BoxPanel.X_AXIS);
            String status = agent.isInstalled()
                    ? (agent.isConfigured() ? " ✓ " + I18n.tr("Configured") : " ○ " + I18n.tr("Not configured"))
                    : " ✗ " + I18n.tr("Not installed");
            JLabel nameLabel = new JLabel(agent.getName());
            JLabel statusLabel = new JLabel(status);
            statusLabel.setForeground(agent.isInstalled()
                    ? (agent.isConfigured() ? new Color(0, 128, 0) : Color.ORANGE)
                    : Color.GRAY);

            JButton configBtn = new JButton(I18n.tr("Configure"));
            configBtn.setEnabled(agent.isInstalled() && !agent.isConfigured());
            configBtn.addActionListener(e -> configureAgent(agent));

            JButton removeBtn = new JButton(I18n.tr("Remove"));
            removeBtn.setEnabled(agent.isConfigured());
            removeBtn.addActionListener(e -> removeAgent(agent));

            row.add(nameLabel);
            row.addHorizontalComponentGap();
            row.add(statusLabel);
            row.addHorizontalComponentGap();
            row.add(configBtn);
            row.addHorizontalComponentGap();
            row.add(removeBtn);

            AGENTS_PANEL.add(row);
        }
        AGENTS_PANEL.revalidate();
        AGENTS_PANEL.repaint();
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

    private void configureAllAgents() {
        String mcpUrl = getMcpUrl();
        CompletableFuture.supplyAsync((Supplier<Integer>) () -> AgentConfigWriter.configureAll(mcpUrl))
                .thenAcceptAsync(count -> GUIMediator.safeInvokeLater(this::refreshAgents));
    }

    private String getMcpUrl() {
        String scheme = TLS_CHECKBOX.isSelected() ? "https" : "http";
        return scheme + "://" + HOST_FIELD.getText().trim() + ":" + PORT_FIELD.getValue() + "/mcp";
    }
}
