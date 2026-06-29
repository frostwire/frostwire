package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import javax.swing.*;

/** Settings pane for IceBridge (decentralized relay / rUDP mesh for distributed search). */
public final class IceBridgeSettingsPaneItem extends AbstractPaneItem {

  private static final String TITLE = I18n.tr("IceBridge");
  private static final String LABEL =
      I18n.tr(
          "Configure the IceBridge relay for decentralized search. Desktop can use its own local IceBridge daemon or connect to a remote one (e.g. standalone relay). For remote mode, use the full base URL of the remote control API (include http:// and the control HTTP port).");

  private final JCheckBox ENABLED_CHECKBOX =
      new JCheckBox(I18n.tr("Enable IceBridge (distributed relay)"));

  private final JRadioButton LOCAL_RADIO =
      new JRadioButton(I18n.tr("Use local IceBridge daemon (fork subprocess)"));
  private final JRadioButton REMOTE_RADIO = new JRadioButton(I18n.tr("Use remote IceBridge relay"));

  // Local settings
  private final JTextField BIND_HOST_FIELD = new SizedTextField(15, SizePolicy.RESTRICT_HEIGHT);
  private final WholeNumberField RUDP_PORT_FIELD =
      new SizedWholeNumberField(6889, 5, SizePolicy.RESTRICT_BOTH);
  private final WholeNumberField RELAY_LISTEN_PORT_FIELD =
      new SizedWholeNumberField(6888, 5, SizePolicy.RESTRICT_BOTH);
  private final JComboBox<String> ROLE_COMBO =
      new JComboBox<>(new String[] {"BOTH", "CLIENT", "FORWARDER"});

  // Remote settings
  private final JTextField REMOTE_URL_FIELD = new SizedTextField(30, SizePolicy.RESTRICT_HEIGHT);
  private final JTextField REMOTE_TOKEN_FIELD = new SizedTextField(30, SizePolicy.RESTRICT_HEIGHT);

  // Sub panels for local vs remote (visibility toggled so unselected mode doesn't waste vertical
  // space)
  private BoxPanel localFieldsPanel;
  private BoxPanel remoteUrlPanel;
  private JLabel remoteUrlExampleLabel;
  private BoxPanel remoteTokenPanel;

  public IceBridgeSettingsPaneItem() {
    super(TITLE, LABEL);

    ButtonGroup modeGroup = new ButtonGroup();
    modeGroup.add(LOCAL_RADIO);
    modeGroup.add(REMOTE_RADIO);

    LOCAL_RADIO.addItemListener(e -> updateRemoteFields());
    REMOTE_RADIO.addItemListener(e -> updateRemoteFields());

    // Prevent components from stretching vertically (the root cause of tall Role dropdown etc.)
    GUIUtils.restrictSize(ENABLED_CHECKBOX, SizePolicy.RESTRICT_HEIGHT);
    GUIUtils.restrictSize(LOCAL_RADIO, SizePolicy.RESTRICT_HEIGHT);
    GUIUtils.restrictSize(REMOTE_RADIO, SizePolicy.RESTRICT_HEIGHT);
    GUIUtils.restrictSize(ROLE_COMBO, SizePolicy.RESTRICT_HEIGHT);

    add(ENABLED_CHECKBOX);
    add(getHorizontalSeparator());

    add(LOCAL_RADIO);
    localFieldsPanel = new BoxPanel(BoxPanel.X_AXIS);
    localFieldsPanel.add(
        new LabeledComponent(
                I18n.tr("Bind host:"),
                BIND_HOST_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    localFieldsPanel.addHorizontalComponentGap();
    localFieldsPanel.add(
        new LabeledComponent(
                I18n.tr("rUDP port:"),
                RUDP_PORT_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    localFieldsPanel.addHorizontalComponentGap();
    localFieldsPanel.add(
        new LabeledComponent(
                I18n.tr("Relay listen port (identity):"),
                RELAY_LISTEN_PORT_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    localFieldsPanel.addHorizontalComponentGap();
    localFieldsPanel.add(
        new LabeledComponent(
                I18n.tr("Role:"), ROLE_COMBO, LabeledComponent.NO_GLUE, LabeledComponent.LEFT)
            .getComponent());
    add(localFieldsPanel);

    add(REMOTE_RADIO);
    remoteUrlPanel = new BoxPanel(BoxPanel.X_AXIS);
    remoteUrlPanel.add(
        new LabeledComponent(
                I18n.tr("Remote URL:"),
                REMOTE_URL_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    add(remoteUrlPanel);

    REMOTE_URL_FIELD.setToolTipText(
        I18n.tr(
            "Full base URL to the remote IceBridge control HTTP API (scheme + host + control port). No URL path suffix is needed unless you front the API with a proxy."));

    // Example shown under the Remote URL field, roughly indented to sit under the text field
    remoteUrlExampleLabel =
        new JLabel(
            "<html><i>"
                + I18n.tr(
                    "Example: http://192.168.1.50:8080   or   http://relay.example.com:8797  (use the control HTTP port)")
                + "</i></html>");
    remoteUrlExampleLabel.setFont(remoteUrlExampleLabel.getFont().deriveFont(11f));
    remoteUrlExampleLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 85, 2, 0));
    add(remoteUrlExampleLabel);
    remoteTokenPanel = new BoxPanel(BoxPanel.X_AXIS);
    remoteTokenPanel.add(
        new LabeledComponent(
                I18n.tr("Auth token (optional):"),
                REMOTE_TOKEN_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    add(remoteTokenPanel);

    REMOTE_TOKEN_FIELD.setToolTipText(
        I18n.tr(
            "Bearer token for X-IceBridge-Token header. Generate one on the remote with --generate-token (or ICEBRIDGE_AUTH_TOKENS_FILE)."));

    add(getVerticalSeparator());
    JLabel note =
        new JLabel(
            "<html><i>"
                + I18n.tr(
                    "Changes require restarting FrostWire. Use remote mode to point desktop at a standalone IceBridge (launched via ./gradlew icebridge).")
                + "</i></html>");
    add(note);

    // Absorb any extra vertical space so all controls stay packed at the top
    // (prevents tall stretched controls and large empty regions).
    add(Box.createVerticalGlue());

    ENABLED_CHECKBOX.addItemListener(e -> updateState());
    updateState();
  }

  private void updateState() {
    boolean enabled = ENABLED_CHECKBOX.isSelected();
    LOCAL_RADIO.setEnabled(enabled);
    REMOTE_RADIO.setEnabled(enabled);
    updateRemoteFields();
  }

  private void updateRemoteFields() {
    boolean remote = REMOTE_RADIO.isSelected();
    boolean enabled = ENABLED_CHECKBOX.isSelected();

    // Hide the controls for the non-selected mode so the pane stays compact
    // (everything pushed toward the top, like gravity="top").
    if (localFieldsPanel != null) localFieldsPanel.setVisible(!remote);
    if (remoteUrlPanel != null) remoteUrlPanel.setVisible(remote);
    if (remoteUrlExampleLabel != null) remoteUrlExampleLabel.setVisible(remote);
    if (remoteTokenPanel != null) remoteTokenPanel.setVisible(remote);

    // Field enables (respect both the top-level enabled checkbox and the mode)
    BIND_HOST_FIELD.setEnabled(enabled && !remote);
    RUDP_PORT_FIELD.setEnabled(enabled && !remote);
    RELAY_LISTEN_PORT_FIELD.setEnabled(enabled && !remote);
    ROLE_COMBO.setEnabled(enabled && !remote);
    REMOTE_URL_FIELD.setEnabled(enabled && remote);
    REMOTE_TOKEN_FIELD.setEnabled(enabled && remote);
  }

  @Override
  public void initOptions() {
    ENABLED_CHECKBOX.setSelected(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    boolean useRemote = SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue();
    if (useRemote) {
      REMOTE_RADIO.setSelected(true);
    } else {
      LOCAL_RADIO.setSelected(true);
    }

    BIND_HOST_FIELD.setText(SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue());
    RUDP_PORT_FIELD.setValue(SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue());
    RELAY_LISTEN_PORT_FIELD.setValue(SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue());
    ROLE_COMBO.setSelectedItem(SearchEnginesSettings.ICEBRIDGE_ROLE.getValue());

    REMOTE_URL_FIELD.setText(SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue());
    REMOTE_TOKEN_FIELD.setText(SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue());

    updateState();
    updateRemoteFields();
  }

  @Override
  public boolean applyOptions() {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(ENABLED_CHECKBOX.isSelected());
    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.setValue(REMOTE_RADIO.isSelected());
    SearchEnginesSettings.ICEBRIDGE_BIND_HOST.setValue(BIND_HOST_FIELD.getText().trim());
    SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.setValue(RUDP_PORT_FIELD.getValue());
    SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.setValue(RELAY_LISTEN_PORT_FIELD.getValue());
    SearchEnginesSettings.ICEBRIDGE_ROLE.setValue((String) ROLE_COMBO.getSelectedItem());
    SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.setValue(REMOTE_URL_FIELD.getText().trim());
    SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.setValue(REMOTE_TOKEN_FIELD.getText().trim());

    // Log the configuration change. The IceBridge child is started only at app launch,
    // so these values take effect after restart.
    com.frostwire.util.Logger log =
        com.frostwire.util.Logger.getLogger(IceBridgeSettingsPaneItem.class);
    log.info("=== IceBridge Configuration (updated via settings; restart required) ===");
    log.info(
        "  ICEBRIDGE_ENABLED             = " + SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    log.info(
        "  ICEBRIDGE_USE_REMOTE          = "
            + SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue());
    log.info(
        "  ICEBRIDGE_REMOTE_URL          = "
            + SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue());
    boolean hasRemoteToken =
        !SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue().isEmpty();
    log.info("  ICEBRIDGE_REMOTE_AUTH_TOKEN   = " + (hasRemoteToken ? "[set]" : "(empty)"));
    log.info(
        "  ICEBRIDGE_BIND_HOST           = "
            + SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue());
    log.info(
        "  ICEBRIDGE_RUDP_PORT           = "
            + SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue());
    log.info(
        "  ICEBRIDGE_RELAY_LISTEN_PORT   = "
            + SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue());
    log.info(
        "  ICEBRIDGE_ROLE                = " + SearchEnginesSettings.ICEBRIDGE_ROLE.getValue());
    log.info(
        "  ICEBRIDGE_CONTROL_HTTP_PORT   = "
            + SearchEnginesSettings.ICEBRIDGE_CONTROL_HTTP_PORT.getValue());
    log.info("======================================================================");
    return false;
  }

  @Override
  public boolean isDirty() {
    return ENABLED_CHECKBOX.isSelected() != SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue()
        || REMOTE_RADIO.isSelected() != SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue()
        || !BIND_HOST_FIELD
            .getText()
            .trim()
            .equals(SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue())
        || RUDP_PORT_FIELD.getValue() != SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue()
        || RELAY_LISTEN_PORT_FIELD.getValue()
            != SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue()
        || !((String) ROLE_COMBO.getSelectedItem())
            .equals(SearchEnginesSettings.ICEBRIDGE_ROLE.getValue())
        || !REMOTE_URL_FIELD
            .getText()
            .trim()
            .equals(SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue())
        || !REMOTE_TOKEN_FIELD
            .getText()
            .trim()
            .equals(SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue());
  }
}
