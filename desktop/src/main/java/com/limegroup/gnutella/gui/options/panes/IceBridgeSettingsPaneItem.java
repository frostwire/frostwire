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
          "Configure the IceBridge relay for decentralized search. Desktop can use its own local IceBridge daemon or connect to a remote one (e.g. standalone relay).");

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

  public IceBridgeSettingsPaneItem() {
    super(TITLE, LABEL);

    ButtonGroup modeGroup = new ButtonGroup();
    modeGroup.add(LOCAL_RADIO);
    modeGroup.add(REMOTE_RADIO);

    LOCAL_RADIO.addItemListener(e -> updateRemoteFields());
    REMOTE_RADIO.addItemListener(e -> updateRemoteFields());

    add(ENABLED_CHECKBOX);
    add(getHorizontalSeparator());

    add(LOCAL_RADIO);
    BoxPanel localPanel = new BoxPanel(BoxPanel.X_AXIS);
    localPanel.add(
        new LabeledComponent(
                I18n.tr("Bind host:"),
                BIND_HOST_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    localPanel.addHorizontalComponentGap();
    localPanel.add(
        new LabeledComponent(
                I18n.tr("rUDP port:"),
                RUDP_PORT_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    localPanel.addHorizontalComponentGap();
    localPanel.add(
        new LabeledComponent(
                I18n.tr("Relay listen port (identity):"),
                RELAY_LISTEN_PORT_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    localPanel.addHorizontalComponentGap();
    localPanel.add(
        new LabeledComponent(
                I18n.tr("Role:"), ROLE_COMBO, LabeledComponent.NO_GLUE, LabeledComponent.LEFT)
            .getComponent());
    add(localPanel);

    add(REMOTE_RADIO);
    BoxPanel remotePanel = new BoxPanel(BoxPanel.X_AXIS);
    remotePanel.add(
        new LabeledComponent(
                I18n.tr("Remote URL:"),
                REMOTE_URL_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    add(remotePanel);
    BoxPanel tokenPanel = new BoxPanel(BoxPanel.X_AXIS);
    tokenPanel.add(
        new LabeledComponent(
                I18n.tr("Auth token (optional):"),
                REMOTE_TOKEN_FIELD,
                LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT)
            .getComponent());
    add(tokenPanel);

    add(getVerticalSeparator());
    JLabel note =
        new JLabel(
            "<html><i>"
                + I18n.tr(
                    "Changes require restarting FrostWire. Use remote mode to point desktop at a standalone IceBridge (launched via ./gradlew icebridge).")
                + "</i></html>");
    add(note);

    ENABLED_CHECKBOX.addItemListener(e -> updateState());
    updateState();
  }

  private void updateState() {
    boolean enabled = ENABLED_CHECKBOX.isSelected();
    LOCAL_RADIO.setEnabled(enabled);
    REMOTE_RADIO.setEnabled(enabled);
    BIND_HOST_FIELD.setEnabled(enabled && LOCAL_RADIO.isSelected());
    RUDP_PORT_FIELD.setEnabled(enabled && LOCAL_RADIO.isSelected());
    ROLE_COMBO.setEnabled(enabled && LOCAL_RADIO.isSelected());
    REMOTE_URL_FIELD.setEnabled(enabled && REMOTE_RADIO.isSelected());
    REMOTE_TOKEN_FIELD.setEnabled(enabled && REMOTE_RADIO.isSelected());
  }

  private void updateRemoteFields() {
    boolean remote = REMOTE_RADIO.isSelected();
    BIND_HOST_FIELD.setEnabled(!remote);
    RUDP_PORT_FIELD.setEnabled(!remote);
    ROLE_COMBO.setEnabled(!remote);
    REMOTE_URL_FIELD.setEnabled(remote);
    REMOTE_TOKEN_FIELD.setEnabled(remote);
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
