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

import com.wittams.gritty.swing.*;

public class SerialTerminal extends Plugin //implements MessageConsumer
{
    public static HashMap<String, String> pluginInfo = null;
    public static void setInfo(HashMap<String, String>info) { pluginInfo = info; }
    public static String getInfo(String item) { return pluginInfo.get(item); }

    ArrayList<String> history = new ArrayList<String>();
    int historySlot = -1;

    JFrame win = null;
//    JTerminal term;

    GrittyTerminal term;
    SerialTty tty;

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
//        win.setResizable(false);




        term = new GrittyTerminal();
        tty = new SerialTty(port);
        term.setFont(Preferences.getFont("plugins.serialterminal.font"));
        term.getTermPanel().setSize(new Dimension(100, 100));
        term.getTermPanel().setAntiAliasing(true);
        term.setTty(tty);

        term.start();

        win.getContentPane().add(term.getTermPanel(), BorderLayout.CENTER);
        win.getContentPane().add(term.getScrollBar(), BorderLayout.EAST);

//        term.setAutoCr(Preferences.getBoolean("pluhins.serialconsole.autocr_in"));
        tty.setAddCR(Preferences.getBoolean("pluhins.serialconsole.autocr_in"));

        shortcuts = new JButton[10];
        Box shortcutBox = Box.createVerticalBox();

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
            shortcutBox.add(shortcuts[i]);
        }

        JLabel edmess = new JLabel("<html><body><center>CTRL+Click<br/>to edit</center></body></html>");
        shortcutBox.add(edmess);

        win.getContentPane().add(shortcutBox, BorderLayout.WEST);

        Box bottomBox = Box.createVerticalBox();
        win.getContentPane().add(bottomBox, BorderLayout.SOUTH);

        Box line = Box.createHorizontalBox();
        bottomBox.add(line);

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

        line.add(localEcho);
        
        showCursor = new JCheckBox(Translate.t("Show Cursor"));
        term.getTermPanel().setCursorEnabled(Preferences.getBoolean("plugins.serialterminal.cursor"));
        showCursor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (ready) {
                    term.getTermPanel().setCursorEnabled(showCursor.isSelected());
                    Preferences.setBoolean("plugins.serialterminal.cursor", showCursor.isSelected());
                }
            }
        });
        
        line.add(showCursor); 
        showCursor.setSelected(Preferences.getBoolean("plugins.serialterminal.cursor"));

        JButton pulse = new JButton("Pulse Line");
        pulse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                port.pulseLine();
            }
        });
        line.add(pulse);

        entryLineArea = Box.createHorizontalBox();

        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    port.print(lineEntryBox.getText());
                    if (historySlot == -1) {
                        history.add(lineEntryBox.getText());
                    }
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

        lineEntryBox = new JTextField();
        lineEntryBox.setBackground(new Color(255, 255, 255));
        lineEntryBox.addActionListener(al);

        lineEntryBox.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {
            }
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case 38: // Up
                        if (historySlot == -1) {
                            historySlot = history.size();
                        }
                        historySlot--;
                        if (historySlot < 0) historySlot = 0;
                        lineEntryBox.setText(history.get(historySlot));
                        break;
                    case 40: // Down
                        if (historySlot == -1) break;
                        historySlot++;
                        if (historySlot >= history.size()) 
                            historySlot = history.size()-1;
                        lineEntryBox.setText(history.get(historySlot));
                        break;
                    default:
                        historySlot = -1;
                        break;
                }
            }
            public void keyReleased(KeyEvent e) {
            }
        });

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
        bottomBox.add(entryLineArea);

//        win.getContentPane().add(box);
        win.setSize(600, 400);
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

 //       term.clearScreen();
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
                
//            term.setDisconnected(false);
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
//        term.showCursor(Preferences.getBoolean("plugins.serialterminal.cursor"));
//        port.addCommsListener(this);

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

//    public void message(String m) {
//        if (port == null) {
//            win.setVisible(false);
//            return;
//        }
//        if (Preferences.getBoolean("plugins.serialterminall.autocr_out")) {
//            m = m.replace("\n", "\r\n");
//        }
//
//        if (localEcho.isSelected()) {
//            tty.feed(m);
//        }
//        try {
//            port.print(m);
//        } catch (Exception e) {
//            Base.error(e);
//        }
//    }
    
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

    public void addPanelsToTabs(JTabbedPane pane,int flags) { }

    public void populateMenu(JPopupMenu menu, int flags) { }

}

