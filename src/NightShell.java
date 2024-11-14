import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
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
import java.util.concurrent.locks.ReentrantLock;

/**
 * Night Shell <br/>
 * by PaperFish, from 2024.11
 */
public class NightShell extends JFrame {
    public static final String VERSION = "Arcane Realm v1.5";
    private final ReentrantLock textLock = new ReentrantLock();
    private final JLabel titleLabel = new JLabel();
    private final JTextPane displayArea = new JTextPane();
    private final JTextPane inputArea = new JTextPane();

    public NightShell(String terminal) {
        setSize(480, 640);
        setResizable(true);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(SOFT_BLACK);
        changeTitleBar(terminal);

        addTextPane(displayArea, false, SOFT_BLACK, BorderLayout.CENTER);
        addTextPane(inputArea, true, HARD_GREY, BorderLayout.SOUTH);
        addDocListeners();
        preconfigure();
        print("%o , by Paperfish\n\n", VERSION, LIGHT_GREY, true);
    }

    private void preconfigure() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.focus", new ColorUIResource(new Color(0, 0, 0, 0)));
        } catch (Exception e) {e.printStackTrace();}
        inputArea.setPreferredSize(new Dimension(getWidth(), 48));
        displayArea.addMouseListener(new MouseInputAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int clickOffset = displayArea.viewToModel2D(e.getPoint());
                for (LinkInfo link : links) {
                    if (clickOffset >= link.start && clickOffset <= link.start+link.length) {
                        requestPicture(link.stamp, link.type); repaintLink(link); return;
            }}}
        });
        if (overloadConfig()) setVisible(true);
        else System.exit(0);
    }
    void requestPicture(String stamp, String type) {}

    private void addDocListeners() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) SHIFT = true;
                if (e.getKeyCode() == KeyEvent.VK_TAB) switchUndecorated();
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (!SHIFT) EnterInput();
                    else {try {inputArea.getDocument().insertString(inputArea.getCaretPosition(), "\n", null);
                    } catch (BadLocationException ex) {ex.printStackTrace();}}
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
        TP.setForeground(NightShell.SOFT_WHITE);
        TP.setBackground(BgC);
        TP.setEditorKit(new WrapEditorKit());

        JScrollPane dSP = new JScrollPane(TP);
        dSP.setBorder(BorderFactory.createEmptyBorder());
        dSP.setVerticalScrollBar(new NightScrollBar(BgC));
        add(dSP, BL);
    }

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

    private static class Range {int start, length; public Range(int a, int l) {start = a-l; length = l;}}
    private final List<Range> hintRange = new ArrayList<>();
    public synchronized void clearWhisper() {
        if (hintRange.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {textLock.lock();
            try {
                Document doc = displayArea.getStyledDocument();
                for (int i = hintRange.size()-1; i>=0; i--) {
                    Range range = hintRange.get(i);
                    doc.remove(range.start, range.length);
                    for (LinkInfo link : links)
                        if (!link.hint && link.start > range.start+range.length)
                            link.move(-range.length);
                    hintRange.remove(i);
                }
            } catch (BadLocationException ignored) {
            } finally {textLock.unlock();}
        });
    }
    private final List<LinkInfo> links = new ArrayList<>();
    private static class LinkInfo {
        int start, length; String stamp; String type; boolean beenClicked = false; boolean hint;
        LinkInfo(int a, int l, String ts, String t, boolean h) {start = a-l; length = l; stamp = ts; type=t; hint=h;}
        public void move(int d) {start += d;}
    }

    public synchronized void paint(int start, int length, Color color) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        displayArea.getStyledDocument().setCharacterAttributes(start, length, attributes, false);
    }

    public synchronized void print(String string, Color color, boolean isHint) {
        SwingUtilities.invokeLater(() -> {textLock.lock();
            try {
                SimpleAttributeSet set = new SimpleAttributeSet();
                StyleConstants.setForeground(set, color);
                Document doc = displayArea.getStyledDocument();
                doc.insertString(doc.getLength(), string, set);
                if (isHint) hintRange.add(new Range(doc.getLength(), string.length()));}
            catch (BadLocationException ignored) {}
            finally {textLock.unlock();}
        });
    }
    public synchronized void print(Message message, boolean isHint) {
        for (int i=0; i<message.words.length; i++) print(message.words[i], message.colors[i], isHint);
    }
    public synchronized void println(Message message, boolean isHint) {print(message, isHint); print("\n", LIGHT_GREY, isHint);}

    public synchronized void print(String words, boolean isHint) {print(NightShell.newWhisper(words), isHint);}
    public synchronized void print(String words, Object objects, Color color, boolean isHint) {print(NightShell.newWhisper(words, objects, color), isHint);}

    public synchronized void printTime(String words) {print(words.replace("%t", nowTime(true)), false);}
    public synchronized void printlnTime() {printTime(" (%t)\n");}
    public synchronized void printlnException(String s, Exception e) {
        print(s, SOFT_GREY, false); print(e.getMessage()+"\n", HARD_RED, false);
    }

    public synchronized void printLinkLines(Message message, boolean withTimestamp) {
        for (int i=0; i<2; i++)
            print(message.words[i], message.colors[i], false);
        printLink(message.words[2], "[图片]", HARD_PINK, "cache", false);
        print((withTimestamp ? ("-> " + message.words[2]) : "") + "\n", SOFT_PINK, false);
    }
    public synchronized void printSharedLinks(Message message) {
        print("服务器的共享资源列表：", true);
        for (String linkName : message.words) printLink(linkName, "["+linkName+"] ", SOFT_PURPLE, "share", true);
        print("\n", true);
    }
    public synchronized void printLink(String stamp, String shown, Color color, String type, boolean isHint) {
        SwingUtilities.invokeLater(() -> {textLock.lock();
            Document doc = displayArea.getStyledDocument();
            try {
                SimpleAttributeSet set = new SimpleAttributeSet();
                StyleConstants.setForeground(set, color);
                doc.insertString(doc.getLength(), shown, set);
                links.add(new LinkInfo(doc.getLength(), shown.length(), stamp, type, isHint));
                if (isHint) hintRange.add(new Range(doc.getLength(), shown.length()));}
            catch (BadLocationException ignored) {}
            finally {textLock.unlock();}
        });
    }
    private synchronized void repaintLink(LinkInfo link) {
        if (!link.beenClicked) {link.beenClicked = true; paint(link.start, link.length, DARK_PINK);}
    }

    public static class Message implements Serializable {
        char type;
        String[] words;
        Color[] colors;
        transient BufferedImage image;
        public Message(char type, String[] words, Color[] colors) {
            this.type = type;
            this.words = words;
            this.colors = colors;
            this.image = new BufferedImage(1, 1, 1);
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
            if (image != null) {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                ImageIO.write(image, "png", byteStream);
                byte[] imageBytes = byteStream.toByteArray();
                out.writeInt(imageBytes.length);
                out.write(imageBytes);
            } else {out.writeInt(0);}
        }
        @Serial private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int length = in.readInt();
            if (length > 0) {
                byte[] imageBytes = new byte[length];
                in.readFully(imageBytes);
                ByteArrayInputStream byteStream = new ByteArrayInputStream(imageBytes);
                image = ImageIO.read(byteStream);
            }
        }
    }

    public static Message newLines(String word, String name, Color main, Color minor) {
        return new Message(':', new String[]{name+" ", nowTime(true)+"\n", word}, new Color[]{main, minor, SOFT_WHITE});
    }
    public static Message newLink(String timestamp, String name, Color main, Color minor) {
        return new Message('_', new String[]{name+" ", nowTime(true)+"\n", timestamp}, new Color[]{main, minor});
    }
    public static Message newShareLinks(String[] stamp) {
        return new Message('=', stamp, new Color[]{});
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
    public static Message new1(String word, Color color) {
        return new Message(':', new String[]{word}, new Color[]{color});
    }
    public static Message newN(int N) {
        return new Message(':', new String[N], new Color[N]);
    }
    public static Message newOrder(String key, Object value) {
        return new Message('/', new String[]{key, value.toString()}, new Color[]{SOFT_WHITE});
    }
    public static Message merge(Message... messages) {
        return new Message(messages[0].type,
                Arrays.stream(messages).flatMap(m -> Arrays.stream(m.words)).toArray(String[]::new),
                Arrays.stream(messages).flatMap(m -> Arrays.stream(m.colors)).toArray(Color[]::new));
    }
    public static Message newImage(BufferedImage image, String stamp) {
        Message m = new1(stamp, SOFT_WHITE); m.type = 'i'; m.image = image; return m;
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
    static final Color SOFT_WHITE = new Color(232, 232, 232);   // Chat
    static final Color LIGHT_GREY = new Color(160, 160, 160);   // Notice
    static final Color SOFT_GREY = new Color(100, 100, 100);    // Whisper,Hint
    static final Color SOFT_BLACK = new Color(8, 8, 8);         // Bg0
    static final Color HARD_GREY = new Color(24, 24, 24);       // Bg1
    static final Color DARK_GREY = new Color(16, 16, 16);       // Bg2
    static final Color HARD_GREEN = new Color(117, 190, 0);     // System Pass
    static final Color LIGHT_GREEN = new Color(30, 231, 140);   // Notice Accept
    static final Color LIGHT_AQUA = new Color(65, 204, 255);
    static final Color DARK_AQUA = new Color(12, 142, 190);
    static final Color SOFT_PURPLE = new Color(166, 93, 255);
    static final Color LIGHT_ORANGE = new Color(255, 193, 78, 255);
    static final Color DARK_ORANGE = new Color(199, 134, 14);
    static final Color HARD_PINK = new Color(255, 140, 174);
    static final Color SOFT_PINK = new Color(187, 144, 211);
    static final Color DARK_PINK = new Color(138, 69, 90);
    static final Color SOFT_RED = new Color(253, 105, 97);      // Notice Reject
    static final Color HARD_RED = new Color(215, 13, 0);        // System Error/Exception
    static final Color DARK_RED = new Color(159, 8, 0);         // System Deny

    static final String Help = "/h";
    static final String ExitSys = "/e";
    static final String EjectOne = "/e";
    static final String TerminalSys = "/t";
    static final String MemberList = "/l";
    static final String SendPicture = "/p";
    static final String ClearWhisper = "/c";
    static final String SharePicture = "/s";
    static final String RequestSharedP = "/r";
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
    static final String RequestImage = "request_image";
    static final String ResourceLoss = "resource_loss";
    static final String INFO = "information";
    static final String TALK = "talk";

    public static String nowTime(boolean normative) {
        if (normative) return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        else return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSSS"));
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


    public void changeTitleBar(String terminal) {
        setUndecorated(true);
        JPanel titleBar = new JPanel();
        titleBar.setLayout(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(getWidth(), 30));
        titleBar.setBackground(Color.DARK_GRAY);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        titleLabel.setForeground(LIGHT_GREY);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        resetTitle(terminal);
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
    public void resetTitle(String terminal) {setTitle(VERSION + " | " + terminal);}
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
                .filter(netIf -> {try {return netIf.isUp() && !netIf.isLoopback() && !netIf.isVirtual();
                } catch (SocketException e) {return false;}})
                .flatMap(NetworkInterface::inetAddresses)
                .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress())
                .map(InetAddress::getHostAddress)
                .findFirst()
                .orElse(null);
    }

    public BufferedImage imageOf(String path, boolean showError) {
        try {return ImageIO.read(new File(path));
        } catch (IOException e) {if (showError) jErrorDialog(null, "图像文件已损坏", "读取失败");
        } return null;
    }
    public BufferedImage imageOf(String stamp, String type) {return imageOf(getImagePathIn(stamp, type), false);}
    public void saveImage_auto(BufferedImage image, String stamp) {
        File file = new File(getImagePathIn(stamp, "cache"));
        try {if (!ImageIO.write(image, "png", file)) jErrorDialog(this, "保存失败，cache目录下已存在相同时间戳的资源", "Error");
        } catch (IOException e) {jErrorDialog(this, "保存失败："+e.getMessage(), "Error");}
    }

    public String chooseImage_manual() {
        JFileChooser chooser = new JFileChooser(config.get("upload"));
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (path.endsWith(".png") && new File(path).exists()) return path;
            else {jErrorDialog(this, "请上传png格式的图片", "格式错误"); return "";}
        } return "0";
    }

    /**
     * 使用系统的默认图片查看器来查看图片：<br/>
     * Windows系统使用explorer打开图片，<br/>
     * Unix-like系统（包括Linux和macOS）使用xdg-open或open打开图片（注意：xdg-open在大多数Linux发行版上可用，但macOS应该使用open）。
     */
    public void showImageOf(String imagePath) {
        String absolute_imagePath = new File(imagePath).getAbsolutePath();
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer", absolute_imagePath).start();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
                new ProcessBuilder(os.contains("mac") ? "open" : "xdg-open", absolute_imagePath).start();
            } else {
                jErrorDialog(null, "未知的操作系统，未能调用图片查看器", "查看失败");
            }
        } catch (IOException e) {jErrorDialog(null, "查看图片时出错："+e.getMessage(), "查看失败");}
    }
    public boolean showImage(String stamp, String type, boolean showError) {
        String path = getImagePathIn(stamp, type);
        if (new File(path).exists()) {showImageOf(path); return true;}
        else if (showError) jErrorDialog(null, "未找到图片资源于："+path, "查看失败"); return false;
    }
    public void save_and_show(BufferedImage image, String stamp) {
        saveImage_auto(image, stamp);
        showImage(stamp, "cache", true);
    }
    public String getImagePathIn(String stamp, String location) {return config.get(location) + stamp + ".png";}

    public void shareImage(String timestamp, String name) {
        String imagePathInCache = getImagePathIn(timestamp, "cache");
        if (new File(imagePathInCache).exists()) {
            String dp = copyFile(imagePathInCache, getImagePathIn(name, "share"));
            if (!dp.isEmpty()) print("成功添加"+timestamp+"名以%o到分享\n", name, LIGHT_GREY, true);
        } else print("错误的时间戳\n", SOFT_RED, true);
    }
    public String copyFile(String sourcePath, String destinationPath) {
        String dp = destinationPath; int i = 0;
        while (new File(destinationPath).exists()) dp = dp.replace(".png", "")+"("+(++i)+")"+".png";
        try {Files.copy(Path.of(sourcePath), Path.of(dp)); return dp;
        } catch (IOException e) {printlnException("文件复制失败：", e); return "";}
    }

    public int jConfirmDialog(Component MF, String message, String title) {
        return JOptionPane.showConfirmDialog(MF, message, title, JOptionPane.OK_CANCEL_OPTION);
    }
    public void jErrorDialog(Component MF, String message, String title) {
        JOptionPane.showMessageDialog(MF, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public synchronized NightShell.Message sharedLinks() {
        List<String> names = new ArrayList<>();
        File[] files = new File(config.get("share")).listFiles();
        if (files != null)
            for (File f : files) {String n = f.getName();
                if (n.endsWith(".png"))
                    names.add(n.replace(".png", ""));}
        return (names.isEmpty()) ? newHint("服务器暂无共享资源") : newShareLinks(names.toArray(new String[0]));
    }

    private Map<String, String> config = new HashMap<>();
    boolean overloadConfig() {return loadConfig("0");}
    boolean loadConfig(String ter) {
        String configPath = "./config/" + ter + ".json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (new File(configPath).exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configPath), StandardCharsets.UTF_8)) {
                config = gson.fromJson(reader, new TypeToken<HashMap<String, String>>(){}.getType());
            } catch (Exception e) {jErrorDialog(null, "读取配置文件时出错：\n"+e.getMessage(), "错误"); return false;}
        } else {
            config.put("cache", "./cache/"+ter+"/");
            config.put("upload", "");
            if ("server".equals(ter)) config.put("share", "./resource/share");
            try (FileWriter writer = new FileWriter(configPath)) {
                gson.toJson(config, writer);
            } catch (Exception e) {jErrorDialog(null, "创建配置文件时出错：\n"+e.getMessage(), "错误"); return false;}
        }
        return !(cannotCreateFolder(config.get("cache")) | ("server".equals(ter) && cannotCreateFolder(config.get("share"))));
    }

    private boolean cannotCreateFolder(String path) {
        File bank = new File(path);
        if (!bank.exists() && !bank.mkdirs()) {
            jErrorDialog(null, "程序无法运行，未知原因无法创建默认目录："+path, "Error"); return true;
        } return false;
    }

    public static void doNothing() {}
}
