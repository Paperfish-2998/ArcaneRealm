import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Night Shell <br/>
 * by PaperFish, from 2024.11
 */
public class NightShell extends JFrame {
    public static final String VERSION = "Arcane Realm v1.8.2";
    public final String Terminal;
    private final String BirthTime = nowTime(3);
    private final ReentrantLock textLock = new ReentrantLock();
    private final JLabel titleLabel = new JLabel();
    private final JTextPane displayArea = new JTextPane();
    private final JTextPane inputArea = new JTextPane();

    public NightShell(String terminal) {
        this.Terminal = terminal;
        setSize(480, 640);
        setResizable(true);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(SOFT_BLACK);
        changeTitleBar();

        addTextPane(displayArea, false, SOFT_BLACK, BorderLayout.CENTER);
        addTextPane(inputArea, true, HARD_GREY, BorderLayout.SOUTH);
        addTransferHandler4TextPane(displayArea);
        addDocListeners();
        preConfigure();
        print("%o , by Paperfish\n\n", VERSION, LIGHT_GREY, true);
    }

    private void preConfigure() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.focus", new ColorUIResource(new Color(0, 0, 0, 0)));
        } catch (Exception e) {throw new RuntimeException(e);}
        inputArea.setPreferredSize(new Dimension(getWidth(), 48));
        displayArea.addMouseListener(new MouseInputAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int clickOffset = displayArea.viewToModel2D(e.getPoint());
                for (LinkInfo link : links) {
                    if (clickOffset >= link.start && clickOffset <= link.start+link.length) {
                        NightShell.this.print("Downloading...", true);
                        requestFile(link.stampx, link.location); repaintLink(link); return;
            }}}
        });
        if (overloadConfig() && tryCreateFile(config.get("log")+BirthTime+".txt")) setVisible(true);
        else System.exit(0);
    }
    void requestFile(String stampx, String type) {}

    private void addDocListeners() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) SHIFT = true;
                if (e.getKeyCode() == KeyEvent.VK_TAB) switchUndecorated();
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (!SHIFT) EnterInput();
                    else {try {inputArea.getDocument().insertString(inputArea.getCaretPosition(), "\n", null);
                    } catch (BadLocationException ex) {throw new RuntimeException(ex);}}
                    e.consume();
                }
            }
            @Override public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) SHIFT = false;
            }
        });
    }
    boolean SHIFT = false;

    void EnterInput() {input = inputArea.getText().trim(); inputArea.setText("");}
    String input = null;

    private void addTextPane(JTextPane TP, boolean editable, Color BgC, String BL) {
        TP.setEditable(editable);
        TP.setFont(DEFAULT_FONT);
        TP.setCaretColor(SOFT_WHITE);
        TP.setForeground(SOFT_WHITE);
        TP.setBackground(BgC);
        TP.setEditorKit(new WrapEditorKit());

        JScrollPane dSP = new JScrollPane(TP);
        dSP.setBorder(BorderFactory.createEmptyBorder());
        dSP.setVerticalScrollBar(new NightScrollBar(BgC));
        add(dSP, BL);
    }

    private void addTransferHandler4TextPane(JTextPane textPane) {
        if (Terminal.equals("Server")) return;
        textPane.setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override public boolean importData(TransferSupport support) {
                if (canImport(support)) {
                    try {
                        Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (data instanceof List<?> rawList && !rawList.isEmpty() && rawList.get(0) instanceof File) {
                            @SuppressWarnings("unchecked")
                            List<File> files = (List<File>) rawList;
                            for (File file : files)
                                sendFile(file.getAbsolutePath());
                            return true;
                        }
                    } catch (Exception ignored) {}
                } return false;
            }
        });
    }
    void sendFile(String path) {}

    public void setDisplayHighlightable(boolean bool) {
        if (bool) displayArea.setHighlighter(new DefaultHighlighter());
        else displayArea.setHighlighter(null);
    }

    public String setFont(String type, int size) {
        Font new_f = new Font(type, Font.PLAIN, size);
        displayArea.setFont(new_f);
        inputArea.setFont(new_f);
        Font curr_f = displayArea.getFont();
        return curr_f.getName() + ", " + curr_f.getSize();
    }
    public void setDefaultFont() {
        displayArea.setFont(DEFAULT_FONT);
        inputArea.setFont(DEFAULT_FONT);
    }

    public void prefillInput(String string) {inputArea.setText(string);}

    private final List<Range> hints = new ArrayList<>();
    private static class Range {
        int start, length;
        public Range(int a, int l) {start = a-l; length = l;}
        int end() {return start+length;}
        void move(int f) {start+=f;}
    }
    public synchronized void clearHint(int log) {
        if ((log==0)&&hints.isEmpty() || ((log==1)&&!logCriteria())) return;
        SwingUtilities.invokeLater(() -> {textLock.lock();
            try {
                Document doc = displayArea.getStyledDocument();
                for (int i = hints.size()-1; i>=0; i--) {
                    Range hint = hints.get(i);
                    doc.remove(hint.start, hint.length);
                    links.stream().filter(e -> !e.hint && e.start>hint.end()).forEach(e -> e.move(-hint.length));
                } hints.clear();
                links.removeIf(e -> e.hint);
                if      (log==1) logging();
                else if (log==2) endLogging();
            } catch (BadLocationException ignored) {
            } finally {textLock.unlock();}
        });
    }
    public synchronized void clearHint() {clearHint(0);}
    public synchronized void tryLog() {clearHint(1);}
    public synchronized void endLog() {clearHint(2);}

    private final List<LinkInfo> links = new ArrayList<>();
    private static class LinkInfo {
        int start, length; String stampx; String location; boolean beenClicked = false; boolean hint;
        LinkInfo(int a, int l, String sx, String o, boolean h) {start=a-l; length=l; stampx=sx; location=o; hint=h;}
        void move(int f) {start+=f;}
        boolean isImage() {return isImageName(stampx);}
    }

    public synchronized void paint(int start, int length, Color color) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        displayArea.getStyledDocument().setCharacterAttributes(start, length, attributes, true);
    }

    public synchronized void print(String string, Color color, boolean isHint, boolean isLink, String stampx, String location) {
        SwingUtilities.invokeLater(() -> {textLock.lock();
            try {
                SimpleAttributeSet set = new SimpleAttributeSet();
                StyleConstants.setForeground(set, color);
                Document doc = displayArea.getStyledDocument();
                doc.insertString(doc.getLength(), string, set);
                if (isLink) links.add(new LinkInfo(doc.getLength(), string.length(), stampx, location, isHint));
                if (isHint) hints.add(new Range(doc.getLength(), string.length()));
                tryLog();}
            catch (BadLocationException ignored) {}
            finally {textLock.unlock();}
        });
    }

    public synchronized void print(String string, Color color, boolean isHint) {
        print(string, color, isHint, false, null, null);
    }
    public synchronized void print(Message message, boolean isHint) {
        for (int i=0; i<message.words.length; i++) print(message.words[i], message.colors[i], isHint);
    }
    public synchronized void println(Message message, boolean isHint) {print(message, isHint); print("\n", LIGHT_GREY, isHint);}

    public synchronized void print(String words, boolean isHint) {print(newWhisper(words), isHint);}
    public synchronized void print(String words, Object objects, Color color, boolean isHint) {print(newWhisper(words, objects, color), isHint);}

    public synchronized void printTime(String words) {print(words.replace("%t", nowTime(2)), false);}
    public synchronized void printlnTime() {printTime(" (%t)\n");}
    public synchronized void printlnException(String s, Exception e) {
        print(s, SOFT_GREY, false); print(e.getMessage()+"\n", HARD_RED, false);
    }

    public synchronized void printLink(String stampx, String shown, String location, boolean isHint) {
        print("["+shown+"]", isImageName(shown)?HARD_PINK:SOFT_PURPLE, isHint, true, stampx, location);
    }
    public synchronized void printLinkLines(Message m, boolean withTimestamp) {
        for (int i=0; i<2; i++)
            print(m.words[i], m.colors[i], false);
        printLink(m.words[2]+m.words[4], m.words[3]+m.words[4], "cache", false);
        print((withTimestamp ? ("-> " + m.words[2]) : "") + "\n", GREY_BLUE, false);
    }
    public synchronized void printSharedLinks(Message m) {
        print("服务器的%o列表：", "共享资源", LIGHT_GREY, true);
        for (String stampx : m.words) {printLink(stampx, stampx, "share", true); print(" ", true);}
        print("\n", true);
    }

    private synchronized void repaintLink(LinkInfo link) {
        if (!link.beenClicked) {link.beenClicked = true; paint(link.start, link.length, link.isImage()?DARK_PINK:DARK_PURPLE);}
    }


    public static class Message implements Serializable {
        char type;
        String[] words;
        Color[] colors;
        transient byte[] fileData;
        public Message(char type, String[] words, Color[] colors) {
            this.type = type;
            this.words = words;
            this.colors = colors;
            this.fileData = new byte[0];
        }
        public String serialized() {
            try {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream OOS = new ObjectOutputStream(byteOut);
                OOS.writeObject(this);
                OOS.close();
                return Base64.getEncoder().encodeToString(byteOut.toByteArray());
            } catch (IOException ignored) {}
            return "";
        }
        @Serial private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            if (fileData != null) {
                out.writeInt(fileData.length);
                out.write(fileData);
            } else {out.writeInt(0);}
        }
        @Serial private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int fileLength = in.readInt();
            if (fileLength > 0) {
                fileData = new byte[fileLength];
                in.readFully(fileData);
            } else {fileData = new byte[0];}
        }
    }

    public static Message newLines(String word, String name, Color main, Color minor) {
        return new Message(':', new String[]{name+" ", nowTime(2)+"\n", word}, new Color[]{main, minor, SOFT_WHITE});
    }
    public static Message newLink(String timestamp, String stamp, String x, String name, Color main, Color minor) {
        return new Message('_', new String[]{name+" ", nowTime(2)+"\n", timestamp, stamp, x}, new Color[]{main, minor});
    }
    public static Message newShareLinks(String[] stampx) {
        return new Message('=', stampx, new Color[]{});
    }
    public static Message newNotice(String word) {
        return new Message(':', new String[]{word}, new Color[]{LIGHT_GREY});
    }
    public static Message newNotice(String sentence, Object object, Color color) {
        String[] words = sentence.split("%o");
        return new Message(':', new String[]{words[0], object.toString(), words[1]},
                new Color[]{LIGHT_GREY, color, LIGHT_GREY});
    }
    public static Message newWhisper(String word) {
        return new Message(':', new String[]{word}, new Color[]{SOFT_GREY});
    }
    public static Message newWhisper(String sentence, Object object, Color color) {
        String[] words = sentence.split("%o");
        return new Message(':', new String[]{words[0], object.toString(), words[1]},
                new Color[]{SOFT_GREY, color, SOFT_GREY});
    }
    public static Message newHint(String word) {
        return new Message('.', new String[]{word}, new Color[]{SOFT_GREY});
    }
    public static Message new0() {return new Message(' ', new String[0], new Color[0]);}
    public static Message new1(String word, Color color) {return new Message(':', new String[]{word}, new Color[]{color});}
    public static Message newN(int N) {return new Message(':', new String[N], new Color[N]);}
    public static Message newOrder(String key, Object value) {
        return new Message('/', new String[]{key, value.toString()}, new Color[]{SOFT_WHITE});
    }
    public static Message merge(Message... messages) {
        return new Message(messages[0].type,
                Arrays.stream(messages).flatMap(m -> Arrays.stream(m.words)).toArray(String[]::new),
                Arrays.stream(messages).flatMap(m -> Arrays.stream(m.colors)).toArray(Color[]::new));
    }
    public static Message newFile(byte[] data, String stampx) {
        Message m = new1(stampx, SOFT_WHITE); m.type = 'f'; m.fileData = data; return m;
    }
    public Message deserialized(String s) {
        try {
            byte[] data = Base64.getDecoder().decode(s);
            ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
            ObjectInputStream OIS = new ObjectInputStream(byteIn);
            Message m = (Message) OIS.readObject();
            OIS.close();
            return m;
        } catch (IOException | ClassNotFoundException e) {
            printlnException("对传输信息进行反序列化时出错：", e);
        }
        return new0();
    }

    static final Font DEFAULT_FONT = new Font("Microsoft YaHei", Font.PLAIN, 16);
    static final Font DEFAULT_TITLE = new Font("Microsoft YaHei", Font.BOLD, 16);
    static final Color SOFT_WHITE = new Color(232, 232, 232);   // Chat
    static final Color LIGHT_GREY = new Color(160, 160, 160);   // Notice
    static final Color SOFT_GREY = new Color(100, 100, 100);    // Whisper,Hint
    static final Color SOFT_BLACK = new Color(8, 8, 8);         // Bg0
    static final Color HARD_GREY = new Color(24, 24, 24);       // Bg1
    static final Color DARK_GREY = new Color(16, 16, 16);       // Bg2
    static final Color HARD_GREEN = new Color(117, 190, 0);     // System Pass
    static final Color LIGHT_GREEN = new Color(30, 231, 140);   // Notice Accept
    static final Color LIGHT_AQUA = new Color(65, 204, 255);    // NewUser Main
    static final Color DARK_AQUA = new Color(11, 122, 164);     // NewUser Miner
    static final Color LIGHT_ORANGE = new Color(255, 193, 78);  // Server Main
    static final Color DARK_ORANGE = new Color(173, 117, 11);   // Server Miner
    static final Color HARD_PINK = new Color(255, 140, 174);    // Picture Link
    static final Color SOFT_PURPLE = new Color(177, 137, 255);  // File Link
    static final Color GREY_BLUE = new Color(128, 139, 164);    // TimeStamp
    static final Color DARK_PINK = new Color(138, 69, 90);      // Clicked Picture Link
    static final Color DARK_PURPLE = new Color(123, 69, 138);   // Clicked File Link
    static final Color SOFT_RED = new Color(253, 105, 97);      // Notice Reject
    static final Color HARD_RED = new Color(215, 13, 0);        // System Error/Exception
    static final Color DARK_RED = new Color(159, 8, 0);         // System Deny

    static final String Help = "/h";
    static final String ExitSys = "/e";
    static final String EjectOne = "/e";
    static final String TerminalSys = "/t";
    static final String MemberList = "/l";
    static final String SendFile = "/f";
    static final String ClearHint = "/c";
    static final String ShareFile = "/s";
    static final String RequestSharedList = "/r";
    static final String RenameRoom = "/rn";
    static final String HostPort = "/host";
    static final String ColorHint = "/color";
    static final String ReColor = "/rec";
    static final String ResetFont = "/ref";
    static final String BanTextHighlight = "/bth";
    static final String AllowTextHighlight = "/ath";
    static final String BanNewClient = "/bnc";
    static final String AllowNewClient = "/anc";
    static final String GuestIPv4 = "guest_ipv4";
    static final String JoinRequest = "join_request";
    static final String JoinReject = "join_reject";
    static final String JoinAccept = "join_accept";
    static final String UpdateTitle = "update_title";
    static final String UnusableName = "unusable_name";
    static final String RequestFile = "request_file";
    static final String ResourceLoss = "resource_loss";
    static final String INFO = "information";
    static final String TALK = "talk";

    public static String nowTime(int format) {
        return switch (format) {
            case 1 -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSSS"));
            case 2 -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm:ss"));
            case 3 -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            default -> LocalDateTime.now().toString();
        };
    }
    public static Color hexColor(String hexadecimal) {
        if (hexadecimal.length() == 8 && hexadecimal.matches("[#0-9a-fA-F]+")) {
            int r = Integer.parseInt(hexadecimal.substring(0, 2), 16);
            int g = Integer.parseInt(hexadecimal.substring(2, 4), 16);
            int b = Integer.parseInt(hexadecimal.substring(4, 6), 16);
            int a = Integer.parseInt(hexadecimal.substring(6, 8), 16);
            return new Color(r, g, b, a);
        }
        return null;
    }


    public void changeTitleBar() {
        setTitle(VERSION+" | "+Terminal);
        setUndecorated(true);
        JPanel titleBar = new JPanel();
        titleBar.setLayout(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(getWidth(), 30));
        titleBar.setBackground(Color.DARK_GRAY);
        titleLabel.setFont(DEFAULT_TITLE);
        titleLabel.setForeground(LIGHT_GREY);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        resetTitle();
        JPanel buttonLabel = new JPanel();
        buttonLabel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonLabel.setOpaque(false);
        JButton minimizeButton = new JButton("-");
        setButton(minimizeButton); buttonLabel.add(minimizeButton);
        JButton closeButton = new JButton("×");
        setButton(closeButton); buttonLabel.add(closeButton);
        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(buttonLabel, BorderLayout.EAST);
        MouseAdapter mouseAdapter = new MouseAdapter() {
            Point initialClick;
            @Override public void mousePressed(MouseEvent e) {initialClick = e.getPoint();}
            @Override public void mouseDragged(MouseEvent e) {
                int thisX = NightShell.this.getLocation().x;
                int thisY = NightShell.this.getLocation().y;
                int X = thisX + e.getX() - initialClick.x;
                int Y = thisY + e.getY() - initialClick.y;
                NightShell.this.setLocation(X, Y);
            }
        };
        titleBar.addMouseListener(mouseAdapter);
        titleBar.addMouseMotionListener(mouseAdapter);
        closeButton.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        minimizeButton.addActionListener(e -> setExtendedState(JFrame.ICONIFIED));
        add(titleBar, BorderLayout.NORTH);
    }
    @Override public void setTitle(String title) {titleLabel.setText(title);}
    public void resetTitle() {setTitle(VERSION+" | "+Terminal);}
    public void switchUndecorated() {
        if (SHIFT) {SHIFT = false;
            setVisible(false);
            dispose();
            setUndecorated(!isUndecorated());
            setVisible(true);
        }
    }
    void setButton(JButton button) {
        button.setFont(new Font("", Font.PLAIN, 24));
        button.setPreferredSize(new Dimension(50, 30));
        button.setForeground(LIGHT_GREY);
        button.setBackground(Color.DARK_GRAY);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
    }
    boolean setPort() {return true;}
    boolean setName() {return true;}

    /**
     * 用于令JTextPane实现自动换行
     */
    private static class WrapEditorKit extends StyledEditorKit {
        @Override
        public ViewFactory getViewFactory() {
            return elem -> switch (elem.getName()) {
                case AbstractDocument.ContentElementName -> new LabelView(elem) {
                    @Override public float getMinimumSpan(int axis)
                    {return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);}
                };
                case AbstractDocument.ParagraphElementName -> new ParagraphView(elem);
                case AbstractDocument.SectionElementName -> new BoxView(elem, View.Y_AXIS);
                case StyleConstants.ComponentElementName -> new ComponentView(elem);
                case StyleConstants.IconElementName -> new IconView(elem);
                default -> new LabelView(elem);
            };
        }
    }

    private static class NightScrollBar extends JScrollBar {
        public NightScrollBar(Color bgc) {
            super(VERTICAL);
            setUnitIncrement(48);
            setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
            setUI(new BasicScrollBarUI() {
                @Override protected void configureScrollBarColors() {trackColor = bgc; thumbColor = DARK_GREY;}
                @Override protected JButton createIncreaseButton(int orientation) {return createEmptyButton();}
                @Override protected JButton createDecreaseButton(int orientation) {return createEmptyButton();}
                private JButton createEmptyButton() {
                    JButton button = new JButton();
                    button.setPreferredSize(new Dimension(0, 0));
                    button.setMinimumSize(new Dimension(0, 0));
                    button.setMaximumSize(new Dimension(0, 0));
                    button.setVisible(false);
                    return button;
                }
                @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                    g.setColor(isThumbHovered ? SOFT_GREY : DARK_GREY);
                    g.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
                }
                @Override protected void installListeners() {
                    super.installListeners();
                    scrollbar.addMouseMotionListener(new MouseAdapter() {
                        @Override public void mouseMoved(MouseEvent e) {updateThumbHoverState(e.getPoint());}
                    });
                    scrollbar.addMouseListener(new MouseAdapter() {
                        @Override public void mouseExited(MouseEvent e) {isThumbHovered = false;scrollbar.repaint();}
                    });
                }
                private boolean isThumbHovered = false;
                private void updateThumbHoverState(Point point) {
                    boolean hovered = getThumbBounds().contains(point);
                    if (hovered != isThumbHovered) {isThumbHovered = hovered; scrollbar.repaint();}
                }
            });
        }
    }

    public static String getLocalIPv4Address() throws SocketException {
        return NetworkInterface.networkInterfaces()
                .filter(netIf -> {try {return netIf.isUp() && !netIf.isLoopback() && !netIf.isVirtual();} catch (SocketException e) {return false;}})
                .flatMap(NetworkInterface::inetAddresses)
                .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress())
                .map(InetAddress::getHostAddress)
                .findFirst()
                .orElse(null);
    }

    public void printColorSpecification() {
        print("以%o为主的信息：所有人都可见的聊天内容\n", "白色", SOFT_WHITE, true);
        print("以%o为主的信息：所有人都可见的系统告示\n", "浅灰", LIGHT_GREY, true);
        print("以%o为主的信息：仅你自己可见的系统提示\n", "深灰", SOFT_GREY, true);
        print("成员有两个特征色彩：\nMainColor[%o]在主格上使用\n", "成员名", LIGHT_AQUA, true);
        print("MinorColor[%o]在宾格上使用\n", "成员名", DARK_AQUA, true);
    }
    public Color[] resetTheColor(String[] cmd) {
        if (cmd.length == 3) {
            Color main, minor;
            if ((main = hexColor(cmd[1])) != null && (minor = hexColor(cmd[2])) != null) {
                return new Color[]{main, minor};
            } else print("错误的格式，示例：/rec 41CCFFFF 0C8EBEFF\n", true);
        } else print("用法（十六进制颜色码）：/rec [MainColor] [MinorColor]\n", true);
        return null;
    }
    public void resetTheFont(String[] cmd) {
        if (cmd.length == 3) {
            String type; int size;
            if (!(type = cmd[1]).isBlank() && cmd[2].matches("[0-9]+") && cmd[2].length() < 4) {
                size = Integer.parseInt(cmd[2]);
                print("当前字体已变更为：%o\n", setFont(type, size), SOFT_GREY, true);
            } else print("错误的格式，示例：/ref KaiTi 16\n（字号不能超过999，常用字体：Microsoft YaHei, KaiTi, SimSun, SimHei, FangSong...）\n", true);
        } else {setDefaultFont(); print("已设为默认字体\n用法：/ref [type] [size]\n", true);}
    }
    public void sharePicture(String[] cmd) {
        if (cmd.length == 3) {
            shareFile(cmd[2], (cmd[1].equals(".")) ? cmd[2] : cmd[1]);
        } else print("未知指令，用法：/S [name] [timestamp]，其中name为给文件所赋名（使用'.'按时间戳赋名），timestamp为文件时间戳\n", true);
    }
    public void checkSharedLinks() {
        Message m = sharedLinks();
        if (m.type == '=') printSharedLinks(m);
        else println(m, true);
    }

    public void saveFile_auto(byte[] data, String stampx) {
        Path path = resolveUniquePath(Path.of(filePathIn(stampx, "cache")));
        saveZipBytesTo(path, data);
    }

    public String chooseFile_manual() {
        TFileChooser chooser = new TFileChooser(config.get("upload"));
        if (chooser.showOpenDialog(this) == TFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getAbsolutePath();
        } return "\0";
    }

    public boolean check_and_show(String stampx, String location, boolean showError) {
        String path = filePathIn(stampx, location);
        if (new File(path).exists()) {
            if (isImageName(path)) showImageOf(path);
            else exploreLocation(path); return true;
        } else if (showError) jErrorDialog(null, prompt.get("FileNotFoundIn")+path); return false;
    }

    public void save_and_show(byte[] data, String stampx) {
        saveFile_auto(data, stampx);
        check_and_show(stampx, "cache", true);
    }

    public void shareFile(String timestamp, String name) {
        File[] files = new File(config.get("cache")).listFiles();
        if (files != null) {
            for (File f : files) {
                String [] n_s = name_suffix(f.getName());
                if (n_s[0].equals(timestamp)) {String dn;
                    if (!(dn = copyFile(f.getAbsolutePath(), filePathIn(name+n_s[1], "share"))).isEmpty()) {
                        print("成功%o"+timestamp, "添加", HARD_GREEN, true);
                        print("名以%o到分享\n", dn, LIGHT_GREY, true);
                    } return;
                }
            } print("未知的时间戳\n", SOFT_RED, true);
        } else print("未找到缓存目录\n", SOFT_RED, true);
    }
    public String copyFile(String sourcePath, String destinationPath) {
        Path destination = resolveUniquePath(Path.of(destinationPath));
        try {
            Files.copy(Path.of(sourcePath), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.getFileName().toString();
        } catch (IOException e) {printlnException("文件复制失败：", e); return "";}
    }

    public int jConfirmDialog(Component MF, String text, String title) {
        return JOptionPane.showConfirmDialog(MF, text, title, JOptionPane.OK_CANCEL_OPTION);
    }
    public void jErrorDialog(Component MF, String text) {
        JOptionPane.showMessageDialog(MF, text, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public synchronized Message sharedLinks() {
        List<String> stampxes = new ArrayList<>();
        File[] files = new File(config.get("share")).listFiles();
        if (files != null)
            for (File f : files) {
                if (f.isDirectory()) stampxes.add(f.getName()+"/");
                else                 stampxes.add(f.getName());
            }
        return (stampxes.isEmpty()) ? newHint("服务器暂无共享资源") : newShareLinks(stampxes.toArray(new String[0]));
    }

    public String filePathIn(String stampx, String location) {return config.get(location) + stampx;}

    private static final String[] imageNames = new String[] {".png", ".jpg", ".jpeg", ".gif", ".bmp"};
    private static boolean isImageName(String fileName) {
        for (String s : imageNames) if (fileName.toLowerCase().endsWith(s)) return true; return false;
    }

    public String[] name_suffix(String stampx) {
        int i = stampx.lastIndexOf(".");
        return (i == -1) ? new String[]{stampx, ""} : new String[]{stampx.substring(0, i), stampx.substring(i)};
    }

    public String getConfig(String item) {return config.get(item);}

    Map<String, String> prompt = new HashMap<>();
    private Map<String, String> config = new HashMap<>();
    boolean overloadConfig() {return loadConfig("0");}
    boolean loadConfig(String ter) {
        boolean isServer = "server".equals(ter);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String promptPath = "./config/prompt.json";
        if (!new File(promptPath).exists()) {jErrorDialog(null, "File "+promptPath+" not found"); return false;}
        if ((prompt = json2map(gson, promptPath)) == null) return false;

        String configPath = "./config/" + ter + ".json";
        File configFile = new File(configPath);
        if (configFile.exists()) {
            if ((config = json2map(gson, configPath)) == null) return false;
        } else {
            config.put("log", "./log/"+ter+"/");
            config.put("cache", "./cache/"+ter+"/");
            config.put("upload", "");
            if (isServer) {
                config.put("share", "./resource/share");
                config.put("defaultPort", "");
            } else config.put("defaultAddress", "");
            try {if (new File(configFile.getParent()).mkdirs() && !configFile.createNewFile()) {
                jErrorDialog(null, prompt.get("XProgram")+prompt.get("XCreateConfigFileIn")+configPath); return false;}
                try (FileWriter writer = new FileWriter(configPath)) {
                    gson.toJson(config, writer);
                }
            } catch (Exception e) {jErrorDialog(null, prompt.get("XProgram")+prompt.get("ErrorCreatingConfigFile")+"\n"+e.getMessage()); return false;}
        }
        boolean shareOK = !"server".equals(ter) || tryCreateFolder(config.get("share"));
        return shareOK && tryCreateFolder(config.get("log")) && tryCreateFolder(config.get("cache"));
    }

    private HashMap<String, String> json2map(Gson gson, String path) {
        try (Reader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, new TypeToken<HashMap<String, String>>(){}.getType());
        } catch (Exception e) {jErrorDialog(null, prompt.get("XProgram")+prompt.get("ErrorReadingConfigFile")+"\n"+e.getMessage()); return null;}
    }

    private boolean tryCreateFolder(String path) {
        File folder = new File(path);
        if (!folder.exists() && !folder.mkdirs()) {
            jErrorDialog(null, prompt.get("XProgram")+prompt.get("XCreateFileIn")+path); return false;
        } return true;
    }
    private boolean tryCreateFile(String path) {
        File file = new File(path);
        try {
            if (!file.exists() && !file.createNewFile()) {
                jErrorDialog(null, prompt.get("XProgram")+prompt.get("XCreateFileIn")+path); return false;
            } else return true;
        } catch (IOException e) {jErrorDialog(null, prompt.get("XProgram")+prompt.get("XCreateFileIn")+path); return false;}
    }

    private static final int MAX_DISPLAY_C = 10000;
    private static final int LOG_DISPLAY_C = 6000;

    private boolean logCriteria() {return displayArea.getText().length() >= MAX_DISPLAY_C;}
    public synchronized void logging() {
        try {
            String text = displayArea.getStyledDocument().getText(0, LOG_DISPLAY_C);
            int ni = text.lastIndexOf('\n');
            int len = (ni > 0) ? ni : text.length();
            text = text.substring(0, len);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(config.get("log")+BirthTime+".txt", true))) {
                writer.write(text);
                displayArea.getStyledDocument().remove(0, len);
                hints.forEach(e -> e.move(-len));
                links.removeIf(e -> e.start<len);
                links.forEach(e -> e.move(-len));
                print("LOG: 日志备份已完成\n", true);
            } catch (Exception e) {printlnException("LOG: 日志备份出现异常：", e);}
        } catch (BadLocationException ignored) {}
    }
    public void endLogging() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(config.get("log")+BirthTime+".txt", true))) {
            writer.write(displayArea.getText());
            print("LOG: 日志备份已完成，安全结束\n", true);
        } catch (Exception e) {jErrorDialog(this, prompt.get("XLog")+e.getMessage());}
    }

    /**
     * A FileChooser that displays thumbnails of image files
     */
    public static class TFileChooser extends JFileChooser {
        private final ExecutorService executor;
        private final Map<File, Icon> thumbnailCache = new ConcurrentHashMap<>();
        public TFileChooser(String currentDirectoryPath) {
            super(currentDirectoryPath);
            this.executor = Executors.newFixedThreadPool(4);
            this.setFileView(new FileView() {
                @Override
                public Icon getIcon(File f) {
                    if (f.isFile() && isImageName(f.getName())) {
                        if (thumbnailCache.containsKey(f))
                            return thumbnailCache.get(f);
                        executor.submit(() -> {
                            Icon icon = generateThumbnail(f);
                            if (icon != null) {
                                thumbnailCache.put(f, icon);
                                SwingUtilities.invokeLater(TFileChooser.this::repaint);
                            }
                        });
                        return UIManager.getIcon("FileView.fileIcon");
                    } return super.getIcon(f);
                }
            });
        }
        @Override public int showOpenDialog(Component parent) throws HeadlessException {
            int result = super.showOpenDialog(parent); shutdownExecutor(); return result;
        }
        @Override public int showSaveDialog(Component parent) throws HeadlessException {
            int result = super.showSaveDialog(parent); shutdownExecutor(); return result;
        }
        private static Icon generateThumbnail(File file) {
            try {
                ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                return new ImageIcon(icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH));
            } catch (Exception ignored) {}
            return null;
        }
        private void shutdownExecutor() {
            executor.shutdown();
            try {if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {executor.shutdownNow();}
            } catch (InterruptedException e) {executor.shutdownNow();}
        }
    }

    /**
     * 返回路径指向文件夹的压缩zip文件夹的byte码
     */
    public byte[] fetchZipBytesOf(String filePath, boolean showError) {
        Path path = Paths.get(filePath);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
             Stream<Path> walk = Files.walk(path)) {
            walk.forEach(file -> {
                try {
                    String entryName = path.relativize(file).toString();
                    if (Files.isDirectory(file)) {
                        if (!entryName.endsWith("/"))
                            entryName += "/";
                        zipOutputStream.putNextEntry(new ZipEntry(entryName));
                        zipOutputStream.closeEntry();
                    } else {
                        zipOutputStream.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zipOutputStream);
                        zipOutputStream.closeEntry();
                    }
                } catch (IOException e) {
                    if (showError) jErrorDialog(null, prompt.get("ErrorZip")+path);
                }
            });
        } catch (IOException e) {
            if (showError) {jErrorDialog(null, prompt.get("FileNotFoundIn")+filePath);}
        } return byteArrayOutputStream.toByteArray();
    }

    /**
     * 检查重名，若重名则将路径文件名尾上(i)
     */
    private Path resolveUniquePath(Path destination) {
        String baseName = destination.getFileName().toString();
        String directory = destination.getParent() != null ? destination.getParent().toString() : "";
        String name = baseName;
        String extension = "";
        int dotIndex = baseName.lastIndexOf(".");
        if (dotIndex != -1) {
            name = baseName.substring(0, dotIndex);
            extension = baseName.substring(dotIndex);
        }
        int counter = 0;
        Path uniquePath = destination;
        while (Files.exists(uniquePath))
            uniquePath = Path.of(directory, name+"("+(++counter)+")"+extension);
        return uniquePath;
    }

    /**
     * 将byte码转为压缩zip文件并解压后保存到路径
     */
    private void saveZipBytesTo(Path path, byte[] bytes) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path resolvedPath = path.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    try (OutputStream outputStream = Files.newOutputStream(resolvedPath)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, len);
                        }
                    }
                } zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            jErrorDialog(this, prompt.get("FailToSave")+e.getMessage());
        }
    }

    /**
     * 使用系统的默认图片查看器来查看图片：<br/>
     * Windows系统使用explorer打开图片，<br/>
     * Unix-like系统（包括Linux和macOS）使用xdg-open或open打开图片（注意：xdg-open在大多数Linux发行版上可用，但macOS应该使用open）。
     */
    public void showImageOf(String imagePath) {
        String absolute_imagePath = new File(imagePath).getAbsolutePath();
        try {String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer", absolute_imagePath).start();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
                new ProcessBuilder(os.contains("mac") ? "open" : "xdg-open", absolute_imagePath).start();
            } else jErrorDialog(null, prompt.get("FailToView")+prompt.get("UnKnownSysToCallViewer"));
        } catch (IOException e) {jErrorDialog(null, prompt.get("FailToView")+prompt.get("ErrorViewingImage")+e.getMessage());}
    }

    /**
     * 打开文件所在的位置（文件夹）并选中文件<br/>
     * Windows系统使用explorer /select<br/>
     * macOS系统使用open -R<br/>
     * Linux系统（部分桌面环境支持，如Nautilus）使用xdg-open
     */
    public void exploreLocation(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("explorer /select,\"" + file.getAbsolutePath() + "\"");
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open -R \"" + file.getAbsolutePath() + "\"");
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    Runtime.getRuntime().exec("xdg-open \"" + file.getParent() + "\"");
                } else jErrorDialog(null, prompt.get("ErrorExploreFolder")+prompt.get("UnsupportedPlatForm")+os);
            } else jErrorDialog(null, prompt.get("FileNotFoundIn")+filePath);
        } catch (Exception e) {jErrorDialog(null, prompt.get("ErrorExploreFolder")+e.getMessage());}
    }

}
