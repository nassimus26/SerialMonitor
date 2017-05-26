package org.nassimus.SerialMonitor;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application {
    private SerialPort comPort;
    private RadioMenuItem radioMenuItem;
    private Menu comPortsMenu;
    private Map<String, SerialPort> comPorts = new HashMap<>();
    private Parent root;
    private EventWatcher disconnectionEventWatcher;
    private EventWatcher reconnectionEventWatcher;
    private TextField sendText;
    private Button clearButton;
    private Button connectToggleButton;

    private TextArea textArea;
    private final AtomicBoolean running = new AtomicBoolean(true);
    @Override
    public void start(Stage primaryStage) throws Exception{

        root = FXMLLoader.load(Thread.currentThread().getContextClassLoader().getResource("MainWin.fxml"));

        MenuBar menuBar = (MenuBar) root.lookup("#AppMenuBar");
        Menu configMenu = menuBar.getMenus().filtered(a->
                "ConfigMenu".equals(a.getId())
        ).get(0);
        sendText = (TextField) root.lookup("#sendText");
        clearButton = (Button) root.lookup("#clearButton");
        connectToggleButton = (Button) root.lookup("#connectToggleButton");
        textArea = (TextArea) root.lookup("#SerialLog");

        comPortsMenu = (Menu) configMenu.getItems().filtered(a->"ComPorts".equals(a.getId())).get(0);
        ToggleGroup toggleGroup = new ToggleGroup();
       new Thread(()->{
                AtomicBoolean firsttime = new AtomicBoolean(true);
                while (running.get()){
                    Platform.runLater(()-> {
                        SerialPort[] serialPorts = SerialPort.getCommPorts();
                        for (SerialPort serialPort : serialPorts) {
                            if (serialPort.getDescriptivePortName().contains("COM")) {
                                if (comPorts.containsKey(serialPort.getSystemPortName()))
                                    continue;
                                RadioMenuItem radioMenuItem = new RadioMenuItem(serialPort.getSystemPortName());
                                radioMenuItem.setToggleGroup(toggleGroup);
                                comPorts.put(radioMenuItem.getText(), serialPort);
                                if (firsttime.get())
                                    onSelectComPort(radioMenuItem);
                                comPortsMenu.getItems().add(radioMenuItem);
                                radioMenuItem.setOnAction(new EventHandler<ActionEvent>() {
                                    public void handle(ActionEvent t) {
                                        onSelectComPort((RadioMenuItem) t.getSource());
                                    }
                                });
                                firsttime.set(false);
                            }
                        }
                        for (MenuItem radioMenuItem : comPortsMenu.getItems()) {
                            if (!comPorts.containsKey(radioMenuItem.getText()))
                                comPortsMenu.getItems().remove(radioMenuItem);
                        }
                    });
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        sendText.setOnKeyReleased(new EventHandler<KeyEvent>() {
            public void handle(KeyEvent t) {
                if (t.getCode()== KeyCode.ENTER && comPort!=null && comPort.isOpen()) {
                    comPort.writeBytes(sendText.getText().getBytes(), sendText.getText().length());
                    sendText.setText("");
                }
            }
        });

        clearButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent t) {
                textArea.clear();
            }
        });
        connectToggleButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent t) {
                if (comPort.isOpen())
                    disconnect();
                else
                    connect();
            }
        });

        primaryStage.setTitle("Serial Monitor");
        primaryStage.setScene(new Scene(root, 400, 375));
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setX(Screen.getPrimary().getBounds().getMaxX()-primaryStage.getScene().getWidth()-20);
        primaryStage.setY(Screen.getPrimary().getBounds().getMaxY()-primaryStage.getScene().getHeight()-50);
        primaryStage.setOnCloseRequest(e -> {
            running.set(false);
            disconnect();
            Platform.exit();
        });
        primaryStage.show();
    }

    public void onSelectComPort(RadioMenuItem radioMenuItem){
        this.radioMenuItem = radioMenuItem;
        radioMenuItem.setSelected(true);
        disconnect();
        comPort = comPorts.get(radioMenuItem.getText());
        Platform.runLater(()-> {
            comPortsMenu.setText("Com Port ( " + comPort.getSystemPortName() + " )");
        });
        connect();
    }

    public void disconnect(){
        if (disconnectionEventWatcher!=null)
            disconnectionEventWatcher.terminate();
        if (reconnectionEventWatcher!=null)
            reconnectionEventWatcher.terminate();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Platform.runLater(()-> {
            connectToggleButton.setText( "Connnect" );
        });
        if(comPort!=null)
            comPort.closePort();
    }

    public void connect(){
        Platform.runLater(()-> {
                    connectToggleButton.setText("Disconnect");
                });
        comPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }
            @Override
            public void serialEvent(SerialPortEvent event)
            {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE || comPort.bytesAvailable()<0)
                    return;
                byte[] newData = new byte[comPort.bytesAvailable()];
                int numRead = comPort.readBytes(newData, newData.length);

                int lastIndex = -1;
                for ( int i = newData.length-1; i>1; i-- )
                    if ( newData[i] == '\n' && newData[i-1] == '+' && newData[i-2] == '\r' ) {
                        lastIndex = i;
                        break;
                    }
                if ( lastIndex !=-1 ){
                    byte[] temp = new byte[newData.length-lastIndex-1];
                    System.arraycopy(newData, lastIndex+1, temp, 0, temp.length);
                    newData = temp;
                    Platform.runLater(()-> {
                        textArea.clear();
                    });
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final String fNewData = new String(newData);
                Platform.runLater(()-> {
                    textArea.appendText(fNewData);
                });
            }
        });
        comPort.openPort();
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        disconnectionEventWatcher = EventWatcher.startEventWatcher(()->{
            return !comPort.isOpen();
            },200,
                ()->{
                    Platform.runLater(()-> {
                        textArea.appendText( "Port disconnected\n" );
                    });
                    reconnectionEventWatcher = EventWatcher.startEventWatcher(()->{
                            comPort.openPort(); return comPort.isOpen();},250,
                            ()->{
                                Platform.runLater(()-> {
                                    textArea.clear();
                                });
                                onSelectComPort(radioMenuItem); });
                });

    }
    public static void main(String[] args) {
        launch(args);
    }
}
