package org.uecide.plugin;

import org.uecide.*;
import org.uecide.debug.*;
import org.uecide.editors.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.zip.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;

import jssc.*;

import say.swing.*;


public class SerialTerminal extends Plugin implements CommsListener,MessageConsumer
{
    public static HashMap<String, String> pluginInfo = null;
    public static void setInfo(HashMap<String, String>info) { pluginInfo = info; }
    public static String getInfo(String item) { return pluginInfo.get(item); }

    JFrame win = null;
    JTerminal term;
    CommunicationPort port;
    JComboBox baudRates;
    JCheckBox showCursor;
    JCheckBox localEcho;
    JCheckBox lineEntry;
    JScrollBar scrollbackBar;

    Context ctx;

    static JTextField fontSizeField;
    static JTextField widthField;
    static JTextField heightField;
    static JCheckBox  autoCrIn;
    static JCheckBox  autoCrOut;

    JTextField lineEntryBox;
    JComboBox lineEndings;
    JButton lineSubmit;

    Box entryLineArea;

    int baudRate;

    boolean ready = false;


    public SerialTerminal(Editor e) { editor = e; }
    public SerialTerminal(EditorBase e) { editorTab = e; }

    String[] history;

    JButton[] shortcuts;


    public void run()
    {
        if (win != null) {
            close();
        }

        Version iconTest = new Version("0.8.8alpha20");

//        if (Base.systemVersion.compareTo(iconTest) < 0) {
//            editor.error("Error: This version of the SerialTerminal plugin requires UECIDE version 0.8.8alpha20 or greater.");
//            return;
//        }

        ctx = editor.getSketch().getContext();

        port = ctx.getDevice();
        if (port == null) {
            ctx.error("Error: You do not have a valid device selected.");
            return;
        }

        Debug.message(this + ": Opening serial terminal on port " + port);


        win = new JFrame(Translate.t("Serial Terminal"));
        win.getContentPane().setLayout(new BorderLayout());
        win.setResizable(false);

        Box box = Box.createVerticalBox();

        Box line = Box.createHorizontalBox();

        term = new JTerminal();
        Font f = Preferences.getFont("plugins.serialterminal.font");
        if (f == null) {
            f = new Font("Monospaced", Font.PLAIN, 12);
            Preferences.set("plugins.serialterminal.font", "Monospaced,plain,12");
        }
        term.setFont(f);
        String cp = Preferences.get("plugins.serialconsole.codepage");
        if (cp == null) {
            cp = "cp850";
        }
        term.loadCodePage("/org/uecide/plugin/SerialTerminal/" + cp + ".cp");
        term.setKeypressConsumer(this);
        term.boxCursor(true);

        int width = 80;
        int height = 24;

        try {
            height = Integer.parseInt(Preferences.get("plugins.serialconsole.height"));
        } catch (Exception e) {
            height = 24;
        }

        try {
            width = Integer.parseInt(Preferences.get("plugins.serialconsole.width"));
        } catch (Exception e) {
            width = 80;
        }

        term.setSize(new Dimension(width, height));
        term.setAutoCr(Preferences.getBoolean("pluhins.serialconsole.autocr_in"));

        Box tsb = Box.createHorizontalBox();

        line.add(term);
        scrollbackBar = new JScrollBar(JScrollBar.VERTICAL);
        scrollbackBar.setMinimum(height);
        scrollbackBar.setMaximum(2000 + height);
        scrollbackBar.setValue(2000);
        scrollbackBar.setVisibleAmount(height);
        scrollbackBar.addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent e) {
                term.setScrollbackPosition(2000 - scrollbackBar.getValue());
            }
        });
        tsb.add(scrollbackBar);

        Box btns = Box.createVerticalBox();

        shortcuts = new JButton[10];

        for (int i = 0; i < 9; i++) {
            String name = Preferences.get("plugins.serialterminal.shortcut." + i + ".name");
            if (name == null) {
                name = "None";
            }
            shortcuts[i] = new JButton(name);
            shortcuts[i].setActionCommand(Integer.toString(i));
            shortcuts[i].addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int i = 0;
                    try {
                        i = Integer.parseInt(e.getActionCommand());
                    } catch (Exception ex) {
                    }
            
                    if ((e.getModifiers() & InputEvent.CTRL_MASK) != 0) {
                        JTextField scname = new JTextField(Preferences.get("plugins.serialterminal.shortcut." + i + ".name"));
                        JTextField scstr = new JTextField(Preferences.get("plugins.serialterminal.shortcut." + i + ".string"));
                        JCheckBox docr = new JCheckBox("CR");
                        JCheckBox dolf = new JCheckBox("LF");

                        docr.setSelected(Preferences.getBoolean("plugins.serialterminal.shortcut." + i + ".cr"));
                        dolf.setSelected(Preferences.getBoolean("plugins.serialterminal.shortcut." + i + ".lf"));
                        final JComponent[] inputs = new JComponent[] {
                            new JLabel("Shortcut Name:"),
                            scname,
                            new JLabel("Shortcut Text:"),
                            scstr,
                            docr,
                            dolf
                        };
                        int res = JOptionPane.showConfirmDialog(win, inputs, "Edit Shortcut", JOptionPane.OK_CANCEL_OPTION);
                        if (res == JOptionPane.OK_OPTION) {
                            Preferences.setBoolean("plugins.serialterminal.shortcut." + i + ".cr", docr.isSelected());
                            Preferences.setBoolean("plugins.serialterminal.shortcut." + i + ".lf", dolf.isSelected());
                            Preferences.set("plugins.serialterminal.shortcut." + i + ".name", scname.getText());
                            Preferences.set("plugins.serialterminal.shortcut." + i + ".string", scstr.getText());
                            shortcuts[i].setText(scname.getText());
                        }
                            
                    } else {
                        port.print(Preferences.get("plugins.serialterminal.shortcut." + i + ".string"));
                        boolean cr = Preferences.getBoolean("plugins.serialterminal.shortcut." + i + ".cr");
                        boolean lf = Preferences.getBoolean("plugins.serialterminal.shortcut." + i + ".cr");
                        if (cr) port.print("\r");
                        if (lf) port.print("\n");
                    }
                }
            });
            btns.add(shortcuts[i]);
        }

        JLabel edmess = new JLabel("<html><body><center>CTRL+Click<br/>to edit</center></body></html>");
        btns.add(edmess);


        tsb.add(btns);
        
        line.add(tsb);
        box.add(line);
        
        line = Box.createHorizontalBox();

        line.add(Box.createHorizontalGlue());

        JLabel label = new JLabel(Translate.t("Line speed") + ": ");
        line.add(label);

        CommsSpeed[] availableSpeeds = port.getSpeeds();
        baudRates = new JComboBox(availableSpeeds);
        baudRates.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (ready) {
                    CommsSpeed value = (CommsSpeed) baudRates.getSelectedItem();
                    baudRate = value.getSpeed();
                    Preferences.setInteger("plugins.serialterminal.speed", baudRate);
                    Debug.message(this + ": Change baud rate " + port);
                    if (!port.setSpeed(baudRate)) {
                        ctx.error("Error: Error changing baud rate: " + port.getLastError());
                    }
                }
            }
        });

        line.add(baudRates);

        localEcho = new JCheckBox(Translate.t("Local Echo"));

        final JFrame subwin = win;
        
        lineEntry = new JCheckBox(Translate.t("Line Entry"));
        lineEntry.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                Preferences.setBoolean("plugins.serialterminal.linemode", lineEntry.isSelected());
                entryLineArea.setVisible(lineEntry.isSelected());
                subwin.pack();
                subwin.repaint();
                lineEntryBox.requestFocusInWindow();
            }
        });

        lineEntry.setSelected(Preferences.getBoolean("plugins.serialterminal.linemode"));

        line.add(lineEntry);

        showCursor = new JCheckBox(Translate.t("Show Cursor"));
        showCursor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (ready) {
                    term.showCursor(showCursor.isSelected());
                    Preferences.setBoolean("plugins.serialterminal.cursor", showCursor.isSelected());
                }
            }
        });
        
        line.add(localEcho);
        line.add(showCursor);

        JButton pulse = new JButton("Pulse Line");
        pulse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                port.pulseLine();
            }
        });
        line.add(pulse);
        box.add(line);

        entryLineArea = Box.createHorizontalBox();

        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    port.print(lineEntryBox.getText());
                    if (((String)lineEndings.getSelectedItem()).equals("Carriage Return")) {
                        port.print("\r");
                    }
                    if (((String)lineEndings.getSelectedItem()).equals("Line Feed")) {
                        port.print("\n");
                    }
                    if (((String)lineEndings.getSelectedItem()).equals("CR + LF")) {
                        port.print("\r\n");
                    }
                    lineEntryBox.setText("");
                    lineEntryBox.requestFocusInWindow();
                } catch (Exception ex) {
                    ctx.error(ex);
                }
            }
        };

        entryLineArea.setVisible(Preferences.getBoolean("plugins.serialterminal.linemode"));
        lineEntryBox = new JTextField();
        lineEntryBox.setBackground(new Color(255, 255, 255));
        lineEntryBox.addActionListener(al);

        lineEndings = new JComboBox(new String[] {"None", "Carriage Return", "Line Feed", "CR + LF"});

        lineEndings.setSelectedItem(Preferences.get("plugins.serialterminal.lineendings"));
        lineEndings.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Preferences.set("plugins.serialterminal.lineendings", (String)lineEndings.getSelectedItem());
            }
        });

        lineSubmit = new JButton("Send");
        lineSubmit.addActionListener(al);
                
        entryLineArea.add(lineEntryBox);
        entryLineArea.add(lineSubmit);
        entryLineArea.add(lineEndings);
        box.add(entryLineArea);

        win.getContentPane().add(box);
        win.pack();

        Dimension size = win.getSize();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        win.setLocationRelativeTo(editor); //((screen.width - size.width) / 2,
                          //(screen.height - size.height) / 2);

        win.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        win.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        Base.setIcon(win);

        term.clearScreen();
        try {
            baudRate = Preferences.getInteger("plugins.serialterminal.speed");
            for (CommsSpeed s : availableSpeeds) {
                if (s.getSpeed() == baudRate) {
                    baudRates.setSelectedItem(s);
                }
            }
            Debug.message(this + ": Open port " + port);
            if (port == null) {
                ctx.error("Error: Unable to open serial port");
                win.dispose();
                win = null;
                return;
            }
                
            term.setDisconnected(false);
            if (!port.openPort()) {
                ctx.error("Error: " + port.getLastError());
                win.dispose();
                win = null;
                return;
            }
            port.setSpeed(baudRate);
        } catch(Exception e) {
            ctx.error("Error: Unable to open serial port:");
            ctx.error(e);
            win.dispose();
            win = null;
            return;
        }
        showCursor.setSelected(Preferences.getBoolean("plugins.serialterminal.cursor"));
        term.showCursor(Preferences.getBoolean("plugins.serialterminal.cursor"));
        port.addCommsListener(this);

        win.setTitle(Translate.t("Serial Terminal") + " :: " + port);
        win.setVisible(true);
        ready = true;
    }

    public void close()
    {
        ready = false;
        for( ActionListener al : baudRates.getActionListeners() ) {
            baudRates.removeActionListener( al );
        }
        if (port != null) {
            port.closePort();
        }
        win.dispose();
        win = null;
        Debug.message(this + ": Closing serial terminal on port " + port);
    }

    public void warning(String m) { ctx.warning(m); }
    public void error(String m) { ctx.error(m); }

    public void message(String m) {
        if (port == null) {
            win.setVisible(false);
            return;
        }
        if (Preferences.getBoolean("plugins.serialterminall.autocr_out")) {
            m = m.replace("\n", "\r\n");
        }

        if (localEcho.isSelected()) {
            term.message(m);
        }
        try {
            port.print(m);
        } catch (Exception e) {
            Base.error(e);
        }
    }
    
    public void addToolbarButtons(JToolBar toolbar, int flags) {
        if (flags == Plugin.TOOLBAR_EDITOR) {

            Version iconTest = new Version("0.8.7z31");

            if (Base.systemVersion.compareTo(iconTest) > 0) {
                editor.addToolbarButton(toolbar, "apps", "serial", "Serial Terminal", new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        run();
                    }
                });
            } else {
                JButton b = new JButton(Base.loadIconFromResource("/org/uecide/plugin/SerialTerminal/console.png"));
                b.setToolTipText("Serial Terminal");
                b.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        run();
                    }
                });
                toolbar.add(b);
            }
        }
    }

    public static void populatePreferences(JPanel p) {
    }

    public static void savePreferences() {
    }

    public static PropertyFile getPreferencesTree() {
        PropertyFile f = new PropertyFile();

        f.set("plugins.name", "Plugins");
        f.set("plugins.type", "section");
        f.set("plugins.serialterminal.name", "Serial Terminal");
        f.set("plugins.serialterminal.type", "section");

        f.set("plugins.serialterminal.font.name", "Terminal Font");
        f.set("plugins.serialterminal.font.type", "fontselect");
        f.set("plugins.serialterminal.font.default", "Monospaced,12,plain");

        f.set("plugins.serialterminal.width.name", "Terminal Width");
        f.set("plugins.serialterminal.width.type", "string");
        f.set("plugins.serialterminal.width.default", "80");

        f.set("plugins.serialterminal.height.name", "Terminal Height");
        f.set("plugins.serialterminal.height.type", "string");
        f.set("plugins.serialterminal.height.default", "25");

        f.set("plugins.serialterminal.autocr_in.name", "Add CR to incoming lines");
        f.set("plugins.serialterminal.autocr_in.type", "checkbox");
        f.setBoolean("plugins.serialterminal.autocr_in.default", false);

        f.set("plugins.serialterminal.autocr_out.name", "Add CR to outgoing lines");
        f.set("plugins.serialterminal.autocr_out.type", "checkbox");
        f.setBoolean("plugins.serialterminal.autocr_out.default", false);

        f.set("plugins.serialterminal.codepage.default", "cp850");

        return f;
    }

    public void releasePort(String portName) {
        if (port == null) {
            return;
        }
        if (portName.equals(port.toString())) {
            close();
        }
    }

    public void commsDataReceived(byte[] bytes) {
        String s = "";
        for (byte b : bytes) {
            int i = ((int)b) & 0xFF;
            s += (char)i;
        }
        term.message(s);
        //term.message(port.readString());
    }

    public void populateMenu(JMenu menu, int flags) {
        if (flags == (Plugin.MENU_TOOLS | Plugin.MENU_MID)) {
            JMenuItem item = new JMenuItem("Serial Terminal");
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    run();
                }
            });
            menu.add(item);
        }
    }

    public static String getPreferencesTitle() {
        return null;
    }

    public void populateContextMenu(JPopupMenu menu, int flags, DefaultMutableTreeNode node) {
    }

    public ImageIcon getFileIconOverlay(File f) { return null; }

    public void commsEventReceived(CommsEvent e) {
    }

    public void addPanelsToTabs(JTabbedPane pane,int flags) { }

    public void populateMenu(JPopupMenu menu, int flags) { }

}

