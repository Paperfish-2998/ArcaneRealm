import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ArcaneRealm: Server
 * by PaperFish, from 2024.11
 */
public class ServerArcane {
    private ServerSocket serverSocket;
    private List<ClientAntenna> clientAntennas;
    private final NightShell shell;
    private boolean allowNewClient;
    private Color mainColor, minorColor;
    private final String serverName = "服务器";
    private String roomName = "";
    private String host = "";
    private int port = -1;
    private boolean END = true;

    public ServerArcane() {
        shell = new NightShell("Server") {
            @Override protected void processWindowEvent(WindowEvent e) {
                if (e.getID() == WindowEvent.WINDOW_CLOSING) {
                    if (!END && jConfirmDialog(this,  prompt.get("TerminateServer"), "Terminate") != 0) return;
                    say(TerminalSys);
                } super.processWindowEvent(e);
            }
            @Override boolean overloadConfig() {return loadConfig("server");}
            @Override void requestFile(String stampx, String location) {check_and_show(stampx, location, true);}
            @Override void EnterInput() {super.EnterInput(); if (setPort()) say(input);}
            @Override boolean setPort() {
                if (host.isBlank()) return false;
                if (port != -1) return true; int P;
                if (!input.isBlank() && input.matches("[0-9]+") && ((P = Integer.parseInt(input)) > -1) && (P < 65536)) {
                    println(new1(input, LIGHT_GREEN), true); port = P; Notify(); return false;
                } else println(new1("无效的端口号", SOFT_RED), true);
                print("设定端口号（0~65535）：", true); return false;
            }
        };
        try {
            String localIPv4 = NightShell.getLocalIPv4Address();
            if (localIPv4 == null) shell.print("获取局域网 IPv4 地址时出现%o，请检查网络连接\n", "未知错误", NightShell.HARD_RED, true);
            else {host = localIPv4; shell.print("设定端口号（0~65535）：", true); shell.prefillInput(shell.getConfig("defaultPort"));}
        } catch (SocketException e) {
            shell.printlnException("获取局域网 IPv4 地址时出错：", e);
        }
        mainColor = NightShell.LIGHT_ORANGE;
        minorColor = NightShell.DARK_ORANGE;
    }

    private synchronized void Notify() {notify();}
    public synchronized void communicate() {
        while (port == -1) {try {wait();} catch (InterruptedException e) {shell.printlnException("错误：", e);}}
        clientAntennas = Collections.synchronizedList(new ArrayList<>());   //todo test
        END = false;
        try {
            serverSocket = new ServerSocket(port);
            shell.clearHint();
            shell.print("位于Ipv4地址: %o 上的服务器已启动\n", host+":"+port, Color.WHITE, false);
        } catch (IOException e) {
            shell.printlnException("创建服务器失败：", e);
            END = true; return;
        }
        roomName = host + ":" + port;
        NewClientWait();
        updateParaText();
        shell.print("讨论间%o，正在等待成员加入", "成功建立", NightShell.HARD_GREEN, false);
        shell.printTime("(%t)...\n\n");
    }

    public void NewClientWait() {
        allowNewClient = true;
        new Thread(() -> {
            try {
                while (!END) new ClientAntenna(this, serverSocket.accept()).EarMonite();
            } catch (IOException ignored) {}
        }).start();
    }

    public boolean test4NewClient(ClientAntenna CA) {
        String[] greeting;
        String visitor = "?";
        boolean nameOK = false;
        try {
            greeting = shell.deserialized(CA.listener.readLine()).words;
            if (!NightShell.GuestIPv4.equals(greeting[0])) {order(CA, NightShell.JoinReject, greeting[0]); return false;}
            CA.IPv4 = greeting[1]; visitor = greeting[1];
            while (!nameOK) {
                greeting = shell.deserialized(CA.listener.readLine()).words;
                if (!NightShell.JoinRequest.equals(greeting[0])) {order(CA, NightShell.JoinReject, greeting[0]); return false;}
                CA.name = greeting[1];
                nameOK = !(banNewClient(CA) || unusableName(CA));
            }
            clientAntennas.add(CA);
            order(CA, NightShell.JoinAccept);
            broadcastT("新的成员已加入，欢迎[%o]！", CA.name, NightShell.LIGHT_AQUA);
            updateParaText();
            return true;
        } catch (IOException e) {
            shell.print("来自%o的访客在连接后取消了访问", visitor, NightShell.LIGHT_GREY, false);
            shell.printlnTime();
        }
        return false;
    }
    private boolean banNewClient(ClientAntenna CA) {
        if (!allowNewClient) {
            order(CA, NightShell.BanNewClient);
            shell.print("已阻挡[%o]的加入", CA.name, NightShell.SOFT_RED, false);
            shell.printlnTime(); return true;
        }
        return false;
    }
    private boolean unusableName(ClientAntenna CA) {
        if (CA.name.contains(serverName)) {
            order(CA, NightShell.UnusableName, "不可用"); return true;
        }
        for (ClientAntenna C : clientAntennas)
            if (C.name.trim().equals(CA.name.trim())) {
                order(CA, NightShell.UnusableName, "重复"); return true;
            }
        return false;
    }

    private void terminate() throws IOException {
        allowNewClient = false;
        clientAntennas.forEach(ClientAntenna::EarClose);
        serverSocket.close();
        clientAntennas.clear();
        shell.print("服务器已关闭\n", false);
        shell.resetTitle("Server");
        shell.endLog();
    }

    private void ejectOne(ClientAntenna C) {
        tell(C, NightShell.newNotice("你被请出了房间"));
        clientAntennas.remove(C);
        broadcastT("服务器将成员[%o]请出了讨论间", C.name, C.minorColor);
        order(C, NightShell.ExitSys);
        C.EarClose();
        updateParaText();
    }

    /**
     * 控制台输入：'/'开头为指令，否则作为内容对所有成员进行广播
     */
    public void say(String words) {
        if (END || words.isEmpty()) return;
        try {
            if (words.charAt(0) == '/') command(words);
            else broadcast(NightShell.newLines(words, serverName, mainColor, minorColor));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 向一位成员讲话
     */
    private synchronized void tell(ClientAntenna c, NightShell.Message message) {c.speaker.println(message.serialized());}
    /**
     * 向全体成员广播（向所有成员讲话，同时打印在服务端控制台）
     */
    private synchronized void tellAll(NightShell.Message message) {clientAntennas.forEach(c -> tell(c, message));}
    private synchronized void broadcast(NightShell.Message message) {tellAll(message); shell.println(message, false);}
    private synchronized void broadcastT(NightShell.Message message) {tellAll(message); shell.print(message, false); shell.printlnTime();}
    private synchronized void broadcastT(String words, Object objects, Color colors) {broadcastT(NightShell.newNotice(words, objects, colors));}
    private synchronized void broadcastLink(NightShell.Message message) {tellAll(message); shell.printLinkLines(message, true);}

    /**
     * 执行服务器指令
     */
    private void command(String M) throws IOException {
        String[] cmd = M.split(" ");
        switch (cmd[0].toLowerCase()) {
            case NightShell.Help -> shell.print(HELP_TEXT, true);
            case NightShell.ColorHint -> shell.printColorSpecification();
            case NightShell.MemberList -> shell.println(getMemberList(true), true);
            case NightShell.HostPort -> shell.println(getHostPort(), true);
            case NightShell.ClearHint -> shell.clearHint();
            case NightShell.BanTextHighlight -> {orderAll(cmd[0]); shell.print("已%o成员选中文本\n", "禁止", NightShell.SOFT_RED, false);}
            case NightShell.AllowTextHighlight -> {orderAll(cmd[0]); shell.print("已%o成员选中文本\n", "允许", NightShell.LIGHT_GREEN, false);}
            case NightShell.BanNewClient -> {allowNewClient = false; broadcastT("讨论间现在%o新成员加入", "禁止", NightShell.SOFT_RED);}
            case NightShell.AllowNewClient -> {allowNewClient = true; broadcastT("讨论间现在%o新成员加入", "允许", NightShell.LIGHT_GREEN);}
            case NightShell.ShareFile -> shell.sharePicture(cmd);
            case NightShell.RequestSharedList -> shell.checkSharedLinks();
            case NightShell.TerminalSys -> {broadcastT("服务器%o了讨论间", "关闭", NightShell.DARK_RED); orderAll(NightShell.TerminalSys); END = true;}
            case NightShell.RenameRoom -> {
                if (cmd.length == 2) {roomName = cmd[1];
                    broadcastT("房间名已更新为[%o]", roomName, NightShell.SOFT_WHITE);
                    updateParaText();
                } else if (cmd.length > 2) shell.print("房间名不能包含空格\n", true);
                else shell.print("未知指令，用法：/RN [name]\n", true);
            }
            case NightShell.ReColor -> {
                Color[] newColors = shell.resetTheColor(cmd);
                if (newColors != null) {mainColor = newColors[0]; minorColor = newColors[1];
                    broadcastT(NightShell.merge(NightShell.newNotice("服务器[%o ", serverName, mainColor),
                            NightShell.newNotice("(%o)]已变更特征色", serverName, minorColor)));}
            }
            case NightShell.ResetFont -> shell.resetTheFont(cmd);
            case NightShell.EjectOne -> {
                if (cmd.length == 2) {
                    if (clientAntennas.stream()
                        .filter(c -> c.name.equals(cmd[1])).findFirst()
                        .map(c -> {ejectOne(c); return false;})
                        .orElse(true)) shell.print("未找到名为[%o]的成员\n", cmd[1], NightShell.LIGHT_GREY, true);
                } else shell.print("未知指令，用法：/E [member]\n", true);
            }
            default -> shell.print("未知指令，/H 查看指令帮助\n", true);
        }
        if (END) terminate();
    }

    /**
     * 处理客户端请求 <- ClientArcane.request()/.report()
     */
    private synchronized void response(ClientAntenna C, NightShell.Message M) {
        if (M.type == '/') {String K = M.words[0];
            if (K.equals(NightShell.TALK)) {
                broadcast(NightShell.newLines(M.words[1], C.name, C.mainColor, C.minorColor));
                return;
            }
            shell.print("Responded to [%o]'s request: ", C.name, C.mainColor, false);
            shell.print(K, NightShell.LIGHT_GREY, false);
            if (K.equals(NightShell.RequestFile))
                shell.print("->"+M.words[1], NightShell.LIGHT_GREY, false);
            shell.printlnTime();
            switch (K) {
                case NightShell.ExitSys -> clientExit(C);
                case NightShell.MemberList -> order(C, NightShell.INFO, getMemberList(false).serialized());
                case NightShell.HostPort -> order(C, NightShell.INFO, getHostPort().serialized());
                case NightShell.ReColor -> {
                    String[] hexColors = M.words[1].split(" ");
                    C.mainColor = NightShell.hexColor(hexColors[0]);
                    C.minorColor = NightShell.hexColor(hexColors[1]);
                    broadcastT(NightShell.merge(NightShell.newNotice("成员[%o ", C.name, C.mainColor),
                            NightShell.newNotice("(%o)]已变更特征色", C.name, C.minorColor)));
                }
                case NightShell.RequestFile -> {
                    String[] stampx_location = M.words[1].split(" ");
                    byte[] data = shell.fetchZipBytesOf(shell.filePathIn(stampx_location[0], stampx_location[1]), false);
                    if (data != null) tell(C, NightShell.newFile(data, M.words[1]));
                    else tell(C, NightShell.newOrder(NightShell.ResourceLoss, 0));
                }
                default -> order(C, K);
            }
        } else if (M.type == 'f') {
            String timestamp = NightShell.nowTime(1);
            String[] stamp_x = shell.name_suffix(M.words[0]);
            shell.saveFile_auto(M.fileData, timestamp + stamp_x[1]);
            broadcastLink(NightShell.newLink(timestamp, stamp_x[0], stamp_x[1], C.name, C.mainColor, C.minorColor));
        }
    }


    /**
     * 向客户端发送指示 -> ClientArcane.execute()
     */
    private synchronized void order(ClientAntenna C, String order, String... args) {
        switch (order) {
            case NightShell.JoinReject, NightShell.UnusableName,
                    NightShell.INFO -> tell(C, NightShell.newOrder(order, args[0]));
            case NightShell.UpdateTitle -> tell(C, NightShell.newOrder(order, roomName+" "+clientAntennas.size()));
            case NightShell.RequestSharedList -> tell(C, shell.sharedLinks());
            default -> tell(C, NightShell.newOrder(order, 0));
        }
    }
    private synchronized void orderAll(String order) {clientAntennas.forEach(c -> order(c, order));}


    private synchronized void clientExit(ClientAntenna C) {
        clientAntennas.remove(C);
        broadcastT("成员[%o]离开了讨论间", C.name, C.mainColor);
        C.EarClose();
        updateParaText();
    }
    private synchronized NightShell.Message getMemberList(boolean withIPv4) {
        int n = clientAntennas.size();
        if (n == 0) return NightShell.newHint("暂无成员");
        NightShell.Message m = NightShell.newN(3*n+3);
        m.colors[0] = NightShell.SOFT_GREY;     m.words[0] = "当前共有";
        m.colors[1] = NightShell.LIGHT_GREY;    m.words[1] = String.valueOf(n);
        m.colors[2] = NightShell.SOFT_GREY;     m.words[2] = "人：";
        for (int i=0; i<3*n; i+=3) {
            ClientAntenna ca = clientAntennas.get(i/3);
            m.colors[i+3] = NightShell.SOFT_GREY; m.words[i+3] = " [";
            m.colors[i+4] = ca.minorColor; m.words[i+4] = ca.name;
            m.colors[i+5] = NightShell.SOFT_GREY; m.words[i+5] = "]";
            if (withIPv4) m.words[i+5] += ca.IPv4 + ", ";
        }
        return m;
    }
    public synchronized NightShell.Message getHostPort() {
        NightShell.Message m = NightShell.newN(2);
        m.words[0] = "服务器IPv4地址: "; m.colors[0] = NightShell.SOFT_GREY;
        m.words[1] = host + ":" + port;  m.colors[1] = NightShell.SOFT_WHITE;
        return m;
    }
    public void updateParaText() {
        shell.setTitle(String.format("%s | 服务器终端 | (在线人数：%d)", roomName, clientAntennas.size()));
        orderAll(NightShell.UpdateTitle);
    }

    /**
     * 客户端天线<br/>
     * 服务器拥有一个天线列表，每个天线对应一个客户端，用于对客户端进行监听与发信。
     * BufferedReader listener - To listen to this client.
     * PrintWriter speaker - To talk to this client.
     */
    public class ClientAntenna {
        private final ServerArcane SA;
        public Socket clientSocket;
        public BufferedReader listener;
        private final PrintWriter speaker;
        private Thread Ear;
        private boolean doListen;
        public String IPv4;
        public String name;
        public Color mainColor;
        public Color minorColor;
        public ClientAntenna(ServerArcane SA, Socket clientSocket) {
            this.SA = SA;
            this.clientSocket = clientSocket;
            try {
                this.listener = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                this.speaker = new PrintWriter(clientSocket.getOutputStream(), true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mainColor = NightShell.LIGHT_AQUA;
            minorColor = NightShell.DARK_AQUA;
        }
        /**
         * 监听客户端的信息
         */
        public void EarMonite() {
            Ear = new Thread(() -> {
                if (SA.test4NewClient(this)) {
                    doListen = true;
                    try {
                        String serMess;
                        while (doListen) {
                            if ((serMess = listener.readLine()) == null || serMess.isBlank()) continue;
                            response(this, shell.deserialized(serMess));
                        }
                    } catch (IOException e) {
                        shell.print("与[%o]的", name, minorColor, false);
                        shell.printlnException("连接已丢失：", e);
                        clientExit(this);
                    }
                } else EarClose();
            });
            Ear.start();
        }
        public void EarClose() {
            doListen = false;
            Ear.interrupt();
            try {
                listener.close();
                speaker.close();
                clientSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final String HELP_TEXT = """
                /H                 指令帮助
                /T                 关闭服务器
                /L                 成员列表
                /C                 清空提示字
                /S                 将资源加入共享
                /R                 查看共享资源列表
                /ref              重设显示字体
                /rec              自定义特征色
                /color           查看色彩规范
                /host            查看地址与端口号
                /bth(/ath)     禁止/允许成员选中文本
                /bnc(/anc)    禁止/允许新成员加入
                /E [member] 将成员请出房间
                /RN [name]  重命名房间
                """;

    public static void main(String[] args) {new ServerArcane().communicate();}
}
