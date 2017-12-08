package com.zgy.translate.activitys;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.LinearGradient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.zgy.translate.R;
import com.zgy.translate.adapters.BluetoothDeviceAdapter;
import com.zgy.translate.adapters.interfaces.BluetoothDeviceAdapterInterface;
import com.zgy.translate.base.BaseActivity;
import com.zgy.translate.domains.dtos.BluetoothDeviceDTO;
import com.zgy.translate.domains.eventbuses.BluetoothConnectEB;
import com.zgy.translate.domains.eventbuses.BluetoothDeviceEB;
import com.zgy.translate.global.GlobalSingleThread;
import com.zgy.translate.global.GlobalStateCode;
import com.zgy.translate.global.GlobalUUID;
import com.zgy.translate.managers.inst.ComUpdateReceiverManager;
import com.zgy.translate.receivers.BluetoothReceiver;
import com.zgy.translate.receivers.interfaces.BluetoothReceiverInterface;
import com.zgy.translate.utils.ClsUtils;
import com.zgy.translate.utils.ConfigUtil;
import com.zgy.translate.utils.StringUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class BluetoothDeviceManagerActivity extends BaseActivity implements BluetoothDeviceAdapterInterface,
        BluetoothReceiverInterface, ConfigUtil.AlertDialogInterface{

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID MY_UUID2 = UUID.fromString("00001102-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_ENABLE_BT = 1;  //请求开启蓝牙

    @BindView(R.id.adm_rv_deviceList) RecyclerView deviceRv;
    @BindView(R.id.adm_tv_deviceBonded) TextView deviceBondedTv;  //已绑定过得蓝牙设备
    @BindView(R.id.adm_tv_deviceBondState) TextView deviceBondStateTv; //显示绑定设备状态


    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDeviceBonded; //已配对设备
    private BluetoothReceiver mBluetoothReceiver = null;
    private ComUpdateReceiverManager receiverManager;
    private ConnectThread mConnectThread;
    private GetInputStreamThread mGetInputStreamThread;

    private BluetoothDeviceAdapter mBluetoothDeviceAdapter;  //搜索到设备
    private List<BluetoothDevice> deviceEBList;  //存放搜索到的蓝牙设备
    private int devicePosition;  //选择蓝牙设备坐标



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_manager);
        ButterKnife.bind(this);
        super.init();
    }

    @Override
    public void initView() {
        baseInit();
    }

    @Override
    public void initEvent() {
        EventBus.getDefault().register(this);
    }

    @Override
    public void initData() {

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    private void baseInit(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            ConfigUtil.showToask(this, "不支持蓝牙功能!");
            finish();
            return;
        }
        Log.i("mybuluetooname", mBluetoothAdapter.getName() + "--" + mBluetoothAdapter.getAddress());

        deviceEBList = new ArrayList<>();
        deviceRv.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        mBluetoothDeviceAdapter = new BluetoothDeviceAdapter(this, deviceEBList, this);
        deviceRv.setAdapter(mBluetoothDeviceAdapter);

        //注册
        receiverManager = new ComUpdateReceiverManager(this);
        receiverManager.register(this);

        if(mBluetoothAdapter.isEnabled()){
            getBondDevice();
        }
    }

    /**开启蓝牙*/
    @OnClick(R.id.start_blue) void startBle(){
        //bluetoothAdapter.enable();  //弹出蓝牙开启确认框
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK){
                Log.i("kaiqi", "开启蓝牙");
                getBondDevice();
            }else{
                ConfigUtil.showToask(this, "蓝牙开启失败，重新开启");
            }
        }
    }

    @OnClick(R.id.stop_blue) void stopBle(){
        if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()){
            scanDevice(false);
            mBluetoothAdapter.disable();
            Log.i("guanbi", "关闭蓝牙");
        }
    }

    /**开启蓝牙搜索*/
    private void scanDevice(boolean enable){
        if(!mBluetoothAdapter.isEnabled()){
            ConfigUtil.showToask(this, "开启蓝牙");
            return;
        }

        if(enable){
            if(deviceEBList != null && deviceEBList.size() != 0){
                deviceEBList.clear();
                mBluetoothDeviceAdapter.notifyDataSetChanged();
            }

            if(mBluetoothAdapter != null){
                mBluetoothAdapter.startDiscovery();
            }
        }else{
            if(mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()){
                mBluetoothAdapter.cancelDiscovery();
            }
        }

    }

    /**获取已绑定过得蓝牙信息*/
    private void getBondDevice(){
        if(mBluetoothAdapter != null){
            Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
            if(devices.size() > 0){
                //getBluetoothHeadset();
                for (BluetoothDevice device : devices){
                    autoConnectDevice(device);
                    Log.i("已绑定蓝牙", device.getName() + device.getAddress());
                }
            }else{
                deviceBondedTv.setText("");
                deviceBondedTv.setVisibility(View.GONE);
                deviceBondStateTv.setText("");
                scanDevice(true);
            }
        }
    }

    /**重新搜索*/
    @OnClick(R.id.refresh) void refreshDiscovery(){
        scanDevice(true);
    }

    /**返回搜索到的设备*/
    @Override
    public void receiverDevice(BluetoothDevice device) {
        deviceEBList.add(device);
        mBluetoothDeviceAdapter.notifyItemInserted(deviceEBList.size() - 1);
    }

    @Override
    public void bongDevice(BluetoothDevice deviceDTO, int position) {
        Log.i("选择蓝牙设备", position + deviceDTO.getName() + deviceDTO.getAddress());
        devicePosition = position;
        try {
            ClsUtils.createBond(deviceDTO.getClass(), deviceDTO);
            //device.createBond();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**返回设备状态*/
    @Override
    public void receiverDeviceState(int state, BluetoothDevice device) {
        switch (state){
            case GlobalStateCode.BONDED:
                Log.i("绑定状态", "已绑定");
                autoConnectDevice(deviceEBList.get(devicePosition));
                deviceEBList.remove(devicePosition);
                mBluetoothDeviceAdapter.notifyItemRemoved(devicePosition);
                break;
            case GlobalStateCode.BONDING:
                Log.i("绑定状态", "绑定中");
                break;
            case GlobalStateCode.BONDNONE:
                Log.i("绑定状态", "没有绑定");
                break;
        }
    }

    /**返回配对结果*/
    @Override
    public void receiverDevicePinState(boolean pin, BluetoothDevice device) {
        if(pin){
            //autoConnectDevice(deviceEBList.get(devicePosition));
            //deviceEBList.remove(devicePosition);
            //mBluetoothDeviceAdapter.notifyItemRemoved(devicePosition);
        }else{
            Log.i("配对", "配对失败");
        }
    }

    /**显示当前连接设备*/
    private void autoConnectDevice(BluetoothDevice device){
        mBluetoothDeviceBonded = device;
        scanDevice(false);
        deviceBondedTv.setVisibility(View.VISIBLE);
        if(StringUtil.isEmpty(device.getName())){
            deviceBondedTv.setText(device.getAddress());
        }else{
            deviceBondedTv.setText(device.getName());
        }
        deviceBondStateTv.setText("正在连接...");
        connect(device);
    }

    @OnClick(R.id.adm_tv_deviceBonded) void deviceBonded(){
        ConfigUtil.showAlertDialog(this, "取消连接", "是否取消已连接设备", this);
    }

    /**点击配对设备取消连接*/
    @Override
    public void confirmDialog() {
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if(mGetInputStreamThread != null){
            mGetInputStreamThread.cancel();
            mGetInputStreamThread = null;
        }
        deviceBondStateTv.setText("");
    }

    @Override
    public void cancelDialog() {
    }

    private BluetoothHeadset mBluetoothHeadset;

    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if(profile == BluetoothProfile.HEADSET){
                mBluetoothHeadset = (BluetoothHeadset) proxy;
                for (BluetoothDevice device : mBluetoothHeadset.getConnectedDevices()){
                    Log.i("mBluetoothHeadset", device.getName() + device.getAddress());
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if(profile == BluetoothProfile.HEADSET){
                mBluetoothHeadset = null;
                Log.i("mBluetoothHeadset", "没有连接");
            }
        }
    };

    private void getBluetoothHeadset(){
        int flag = -1;
        int a2dp = mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP);
        int headset = mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
        int health = mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEALTH);

        if(BluetoothProfile.STATE_CONNECTED == a2dp){
            flag = a2dp;
        }else if(BluetoothProfile.STATE_CONNECTED == headset){
            flag = headset;
        }else if(BluetoothProfile.STATE_CONNECTED == health){
            flag = health;
        }

        Log.i("flag--", flag + a2dp + headset + health +"");

        if(flag != -1){
            mBluetoothAdapter.getProfileProxy(this, mProfileListener, flag);
        }
    }


    /**进行蓝牙耳机连接*/
    private synchronized void connect(BluetoothDevice device){
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        BluetoothDevice netDevice = mBluetoothAdapter.getRemoteDevice(device.getAddress());

        mConnectThread = new ConnectThread(netDevice);
        mConnectThread.start();
    }

    /**与蓝牙耳机建立连接*/
    private class ConnectThread extends Thread{
        private final BluetoothSocket mSocket;

        public ConnectThread(BluetoothDevice device){
            BluetoothSocket socket = null;
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);

            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = socket;
        }

        @Override
        public void run() {
            BluetoothConnectEB connectEB = new BluetoothConnectEB();
            try {
                if(mSocket.isConnected()){
                    Log.i("蓝牙已连接", "蓝牙已连接");
                    return;
                }
                mSocket.connect();
                connectEB.setFlag(true);
                connected(mSocket);
            } catch (IOException e) {
                e.printStackTrace();
                if(e.getMessage().contains("closed") || e.getMessage().contains("timeout")){
                    Log.i("连接失败日志", "连接关闭或超时，重新连接");
                }else{
                    Log.i("连接失败日志", "连接失败，重新连接");
                }
                try {
                    mSocket.close();
                    connectEB.setFlag(false);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }

            EventBus.getDefault().post(connectEB);
        }

        public void cancel(){
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**连接成功监听蓝牙耳机输入流*/
    private synchronized void connected(BluetoothSocket socket){
        /*if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }*/

        if(mGetInputStreamThread != null){
            mGetInputStreamThread.cancel();
            mGetInputStreamThread = null;
        }

        mGetInputStreamThread = new GetInputStreamThread(socket);
        mGetInputStreamThread.start();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void returnConnectResult(BluetoothConnectEB connectEB){
        if(connectEB.isFlag()){
            Log.i("连接成功", "连接成功");
            deviceBondStateTv.setText("连接成功");
        }else{
            Log.i("连接失败", "连接失败");
            deviceBondStateTv.setText("");
        }
    }

    /**获取蓝牙输入流线程*/
    private class GetInputStreamThread extends Thread{
        private final BluetoothSocket mSocket;
        private final InputStream mInputStream;

        private GetInputStreamThread(BluetoothSocket socket){
            mSocket = socket;
            InputStream is = null;

            try {
                is = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mInputStream = is;
            Log.i("mScoket---", mSocket +"");
            Log.i("mInputStream---", mInputStream +"");
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true){
                try {
                    int bytesAvailable = mInputStream.available();
                    if(bytesAvailable > 0){
                        bytes = mInputStream.read(buffer);
                        Log.i("bytes--", bytes + "");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if(e.getMessage().contains("closed")){
                        Log.i("耳机关闭", "请检查蓝牙耳机是否开启");
                        break;
                    }
                }
            }
        }

        public void cancel(){
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getBluetoothInputStream(){
        if(GlobalSingleThread.bltInputStreamExecutorService == null){
            GlobalSingleThread.bltInputStreamExecutorService = Executors.newSingleThreadScheduledExecutor();
        }

        GlobalSingleThread.bltInputStreamExecutorService.submit(new Runnable() {
            @Override
            public void run() {
               /* while (mBluetoothSocket != null){
                    byte[] buff = new byte[1024];
                    int bytes;
                    InputStream inputStream;
                    try {
                        inputStream = mBluetoothSocket.getInputStream();
                        bytes = inputStream.read(buff);
                        Log.i("bytes", bytes + "");
                        processBuffer(buff, 1024);
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            mBluetoothSocket.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }

                }*/
            }
        });
    }

    private void processBuffer(byte[] buff, int size){
        int length = 0;
        for (int i = 0 ; i < size ; i++){
            if(buff[i] > '\0'){
                length++;
            }else{
                break;
            }
        }

        byte[] newBuff = new byte[length];
        for (int j = 0 ; j < length ; j++){
            newBuff[j] = buff[j];
        }

        Log.i("蓝牙耳机输入字符串", new String(newBuff));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            if(keyCode == KeyEvent.KEYCODE_MEDIA_PLAY){
                Log.i("按下", "按下按钮");
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_UP){
            if(keyCode == KeyEvent.KEYCODE_MEDIA_PLAY){
                Log.i("抬起", "抬起按钮");
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if(deviceEBList != null && deviceEBList.size() != 0){
            deviceEBList.clear();
            deviceEBList = null;
        }
        if(mBluetoothDeviceAdapter != null){
            mBluetoothDeviceAdapter = null;
            deviceRv.setAdapter(null);
            deviceRv.setLayoutManager(null);
        }
        if(receiverManager != null){
            receiverManager.unRegister();
        }
        if(mBluetoothAdapter != null){
            mBluetoothAdapter = null;
        }
    }
}
