package netac.myusbhost;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final static String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private EditText pid_et, vid_et, param1_et, param2_et, param3_et, param4_et, log_et;
    private Button search_device_btn, commit_btn, connect_device_btn, clear_btn;

    private UsbManager mUsbManager;
    private PendingIntent mPendingIntent;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mInEndpoint, mOutEndpoint;

    private final int DEFAULT_PID=3072, DEFAULT_VID=3544;
    private byte[] cmd=new byte[]{0x7e, 0x00, 0x00, 0x00};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
        initEvent();
    }

    private void initView(){
        pid_et= (EditText) findViewById(R.id.pid_et);
        vid_et= (EditText) findViewById(R.id.vid_et);
        param1_et= (EditText) findViewById(R.id.param1_et);
        param2_et= (EditText) findViewById(R.id.param2_et);
        param3_et= (EditText) findViewById(R.id.param3_et);
        param4_et= (EditText) findViewById(R.id.param4_et);
        log_et= (EditText) findViewById(R.id.log_et);

        search_device_btn= (Button) findViewById(R.id.search_device_btn);
        commit_btn= (Button) findViewById(R.id.commit_btn);
        connect_device_btn= (Button) findViewById(R.id.connect_device_btn);
        clear_btn= (Button) findViewById(R.id.clear_btn);
    }

    private void initData(){

        mUsbManager= (UsbManager) getSystemService(Context.USB_SERVICE);
        mPendingIntent=PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mReceiver, intentFilter);
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addDataScheme("file");
        registerReceiver(mReceiver, filter);

        pid_et.setText(String.valueOf(DEFAULT_PID));
        vid_et.setText(String.valueOf(DEFAULT_VID));
    }

    private void initEvent(){
        search_device_btn.setOnClickListener(mClickListener);
        commit_btn.setOnClickListener(mClickListener);
        connect_device_btn.setOnClickListener(mClickListener);
        clear_btn.setOnClickListener(mClickListener);
    }

    View.OnClickListener mClickListener=new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            switch (view.getId()){
                case R.id.search_device_btn:
                    searchDevice();
                    break;
                case R.id.commit_btn:
                    commitCmd();
                    break;
                case R.id.connect_device_btn:
                    connectDevice();
                    break;
                case R.id.clear_btn:
                    clearLog();
                    break;
                default:
                    break;
            }
        }
    };

    private void log(String formart, Object... args){
        String msg=String.format(formart, args)+"\n";
        mHandler.sendMessage(mHandler.obtainMessage(1, msg));
    }

    private void appendlog(String msg){
        log_et.append(msg);
        log_et.setSelection(log_et.length());
        ((ScrollView)log_et.getParent()).fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void clearLog(){
        mHandler.sendEmptyMessage(2);
    }

    Handler mHandler=new Handler(){

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
                    appendlog(msg.obj.toString());
                    break;
                case 2:
                    log_et.setText("");
                    break;
            }
        }
    };








    //查找设备
    private void searchDevice(){
        Map<String, UsbDevice> deviceMap = mUsbManager.getDeviceList();
        for(UsbDevice device : deviceMap.values()){
            log("search device PID=%s, VID=%s", device.getProductId(), device.getVendorId());
            if(device.getProductId()==Integer.parseInt(pid_et.getText().toString()) && device.getVendorId()==Integer.parseInt(vid_et.getText().toString())){
                mUsbDevice=device;
                log("find device success.");
                break;
            }
        }
        if(mUsbDevice==null){
            log("find device faild.");
            return;
        }


        if(mUsbManager.hasPermission(mUsbDevice)){
            log("device hasPermission.");
            initDevice();
        }else{
            mUsbManager.requestPermission(mUsbDevice, mPendingIntent);
            log("device not has Permission, requestPermission.");
        }
    }

    //初始化和设备通信的相关信息
    private void initDevice(){
        if(mUsbDevice==null)return;
        for(int i=0; i<mUsbDevice.getInterfaceCount(); i++){
            UsbInterface usbInterface=mUsbDevice.getInterface(i);
            log("search device interface class="+usbInterface.getInterfaceClass());
            if(usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_HID || usbInterface.getInterfaceClass()==UsbConstants.USB_CLASS_MASS_STORAGE){
                mUsbInterface=usbInterface;
                log("find a mass storage or hid device usbinterface.");
                for(int j=0; j<usbInterface.getEndpointCount(); j++){
                    UsbEndpoint endpoint=usbInterface.getEndpoint(j);
                    log("UsbEndpoint getType=%s, getDirection=%s, getAddress=%s", endpoint.getType(), endpoint.getDirection(), endpoint.getAddress());
                    if(endpoint.getDirection()==UsbConstants.USB_DIR_OUT){
                        mOutEndpoint=endpoint;
                        log("find out USBEndPoint.");
                    }else if(endpoint.getDirection()==UsbConstants.USB_DIR_IN){
                        mInEndpoint=endpoint;
                        log("find in USBEndPoint.");
                    }
                }
            }
        }
        mUsbInterface=mUsbDevice.getInterface(0);
        mOutEndpoint=mUsbInterface.getEndpoint(0);
    }

    private void connectDevice(){
        if(mUsbDevice==null)return;
        mUsbDeviceConnection=mUsbManager.openDevice(mUsbDevice);
        log("open device " + ((mUsbDeviceConnection!=null)?"success.":"faild."));
    }

    private void commitCmd(){
        if(mUsbDeviceConnection==null || mUsbInterface==null)return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean force=true;
//                log("mUsbDeviceConnection.claimInterface(%s,%s)", mUsbInterface.getInterfaceClass(), force);
//                force = mUsbDeviceConnection.claimInterface(mUsbInterface, force);
//                log("claimInterface=%s", force);
                if(!force)return;
                cmd=new byte[]{0x7e, 0x00, 0x00, 0x00};
                if(!TextUtils.isEmpty(param2_et.getText()))cmd[1]=Byte.parseByte(param2_et.getText().toString(), 16);
                if(!TextUtils.isEmpty(param3_et.getText()))cmd[2]=Byte.parseByte(param3_et.getText().toString(), 16);
                if(!TextUtils.isEmpty(param4_et.getText()))cmd[3]=Byte.parseByte(param4_et.getText().toString(), 16);
                int sendLen = mUsbDeviceConnection.bulkTransfer(mOutEndpoint, cmd, cmd.length, 300);
                log("send length=%s, data=[%s][%s][%s][%s]", sendLen, cmd[0], cmd[1], cmd[2], cmd[3]);
                byte[] reciver = new byte[mOutEndpoint.getMaxPacketSize()];
                int reciverLen = mUsbDeviceConnection.bulkTransfer(mOutEndpoint, reciver, reciver.length, 300);
                log("reciver length=%s, data=%s", reciverLen, new String(reciver));
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean cla = mUsbDeviceConnection.claimInterface(mUsbInterface, true);
                    log("claimInterface=%s", cla);
                    for(int i=0; i<2; i++){
                        log("第%s发送指令",String.valueOf(i+1));
                        reset();
                        getMaxLnu();
                        sendCommand();
                    }
//                      sendCommand();
                } catch (Exception e) {
                    log("error e=%s", e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private void reset() {
        synchronized (this) {
            if (mUsbDeviceConnection != null) {
                // 复位命令的设置有USB Mass Storage的定义文档给出
                int result = mUsbDeviceConnection.controlTransfer(0x21, 0xFF, 0x00, 0x00, null, 0, 1000);
                if(result < 0) {                      // result<0说明发送失败
                    log("Send reset command failed!");
                } else {
                    log("Send reset command succeeded!");
                }
            }
        }
    }


    private void getMaxLnu() {
        synchronized (this) {
            if (mUsbDeviceConnection != null) {
                String str="";
                // 接收的数据只有1个字节
                byte[] message = new byte[1];
                // 获取最大LUN命令的设置由USB Mass Storage的定义文档给出
                int result = mUsbDeviceConnection.controlTransfer(0xA1, 0xFE, 0x00, 0x00, message, 1, 1000);
                if(result < 0) {
                    log("Get max lnu failed!");
                } else {
                    log("Get max lnu succeeded!");
                    for(int i=0; i<message.length; i++) {
                        str += Integer.toString(message[i]&0x00FF);
                    }
                }
                log(str);
            }
        }
    }

    private void sendCommand() {

        String str="";

        byte[] cmd = new byte[] {
                (byte) 0x55, (byte) 0x53, (byte) 0x42, (byte) 0x43, // 固定值
                (byte) 0x28, (byte) 0xe8, (byte) 0x3e, (byte) 0xfe, // 自定义,与返回的CSW中的值是一样的
                (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00, // 传输数据长度为512字节
                (byte) 0x7e, (byte) 0x01, // 传入数据
                (byte) 0x00, // LNU为0,则设为0
                (byte) 0x02, // 命令长度为1
                (byte) 0x23, (byte) 0x00, (byte) 0x00, (byte) 0x00, // READ FORMAT CAPACITIES,后面的0x00皆被忽略
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
        };
        int result = mUsbDeviceConnection.bulkTransfer(mOutEndpoint, cmd, cmd.length, 1000);
        if(result < 0) {
            log("Send command failed!");
        } else {
            log("Send command succeeded!");
        }

        byte[] message = new byte[24];      //  需要足够的长度接收数据
        result = mUsbDeviceConnection.bulkTransfer(mOutEndpoint, message, message.length, 1000);
        if(result < 0) {
            log("Receive message failed!");
        } else {
            log("Receive message succeeded!");
            for(int i=0; i<message.length; i++) {
                str += Integer.toHexString(message[i]&0x00FF) + " ";
            }
            log(str);
        }
        str="";
        byte[] csw = new byte[13];
        result = mUsbDeviceConnection.bulkTransfer(mOutEndpoint, csw, csw.length, 1000);
        if(result < 0) {
            log("Receive CSW failed!");
        } else {
            log("Receive CSW succeeded!");
            for(int i=0; i<csw.length; i++) {
                str += Integer.toHexString(csw[i]&0x00FF) + " ";
            }
        }
        str += "\n";
        log(str);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mUsbDeviceConnection!=null){
            if(mUsbInterface!=null) System.out.println("release interface="+mUsbDeviceConnection.releaseInterface(mUsbInterface));
            mUsbDeviceConnection.close();
            System.out.println("device connect close");
        }
    }

    BroadcastReceiver mReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(ACTION_USB_PERMISSION.equals(action)){

                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device!=null){
                    log("action device hasPermission.");
                    mUsbDevice=device;
                    //USB 设备获取到执行授权
                    initDevice();
                }
                log("action device not hasPermission.");

            }else if(Intent.ACTION_MEDIA_EJECT.equals(action)){
                log("设备被拔出");
                if(mUsbDeviceConnection!=null){
                    if(mUsbInterface!=null) log("release interface="+mUsbDeviceConnection.releaseInterface(mUsbInterface));
                    mUsbDeviceConnection.close();
//                    mUsbDeviceConnection=null;
//                    mUsbInterface=null;
                    log("device connect close");
                }
            }else if(Intent.ACTION_MEDIA_MOUNTED.equals(action)){
                log("设备已插入");
            }
        }
    };
}
