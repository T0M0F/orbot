package org.torproject.android.ui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.torproject.android.service.OrbotService;
import org.torproject.android.service.TorServiceConstants;
import org.torproject.android.service.util.Prefs;

import static org.torproject.android.service.TorServiceConstants.ACTION_START;
import static org.torproject.android.service.TorServiceConstants.ACTION_STOP_VPN;
import static org.torproject.android.service.TorServiceConstants.STATUS_OFF;
import static org.torproject.android.service.TorServiceConstants.STATUS_ON;
import static org.torproject.android.service.TorServiceConstants.STATUS_STARTING;
import static org.torproject.android.service.TorServiceConstants.STATUS_STOPPING;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QuickSettingsTileService extends TileService {

    /*
    Todo: Check how notification gets updated. Especially if app not open. How does notification receive broadcasts? Is OrbotMainActiviy active when starting via Tile? If so the broadcast receiver at the beginning could be interesting. Maybe make tile listening from there?
    Todo: Sync on state from app to tile. Find better solution. Currently either a) STATUS_STARTING is activating tile or we need to delay unregisterBrodcastreciver....
    Todo: Set UI state correctly when adding tile
    Todo: Without vpn mode turning on/off seems to work. However vpn mode crashes frequently with ERROR(tun2socks): tcp_bind_to_netif failed. Check if related to my changes. check if broadcasts are send in case of failure
    Todo: If orbot crashes the title state gets not updated
    Todo: vpn always on mode?
    Todo: check if tor is started/stopped correctly
    Todo: What happens when activating/deactivating tor or tile quickly nacheinander?
    Todo: Clean code
     */

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        System.out.println("--------------------------TILE ADDED---------------------------------");
        registerBroadcastReceiver();
        requestTorStatus();
        Handler handler =  new Handler();
        Runnable runnable = new Runnable() {
            public void run() {
                System.out.println("Unregister!!!!!!!!!!!!!!!");
                unregisterBroadcastReceiver();
            }
        };
        handler.postDelayed(runnable, 15000);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        registerBroadcastReceiver();
        requestTorStatus();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onClick() {
        super.onClick();
        int tileState = getQsTile().getState();
        if(tileState == Tile.STATE_INACTIVE) {
            startTor();
        } else if (tileState == Tile.STATE_ACTIVE) {
            stopTor();
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        System.out.println("-------------STOP LISTENING__________________----");
        Handler handler =  new Handler();
        Runnable runnable = new Runnable() {
            public void run() {
                System.out.println("Unregister!!!!!!!!!!!!!!!");
                unregisterBroadcastReceiver();
            }
        };
        handler.postDelayed(runnable, 15000);
    }

    private void registerBroadcastReceiver() {
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.ACTION_STATUS));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_BANDWIDTH));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_LOG));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_PORTS));
    }

    private void unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);
    }

    private void requestTorStatus() {
        sendIntentToService(TorServiceConstants.ACTION_STATUS);
    }

    private void startTor() {
        sendIntentToService(ACTION_START);
    }

    private void stopTor() {
        if (Prefs.useVpn()) sendIntentToService(ACTION_STOP_VPN);

        Intent intent = new Intent(QuickSettingsTileService.this, OrbotService.class);
        stopService(intent);
    }

    private void sendIntentToService(final String action) {
        Intent intent = new Intent(QuickSettingsTileService.this, OrbotService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void updateTile(String status) {
        System.out.println("--------------------------------------updateTile-----------------------------------");
        switch (status) {
            /*
             * STATUS_ON and STATUS_STARTING both lead to the tile state STATE_ACTIVE, because due to asynchronicity the updateTile method gets called before the localBroadcastReceiver receive STATUS_ON. An example:
             * - OrbotMainActivity.toggleTor() gets called to start Tor
             * - OrbotService starts Tor
             * - TileService.requestListeningState() is called from OrbotMainActivity.toggleTor() to update Tile
             * - TileService registers localBroadcastReceiver upon entering TileService.onStartListening() method
             * - localBroadcastReceiver receives STATUS_STARTING
             * - The TileService.onStopListening method fo TileService gets called and unregisters the localBroadcastReceiver
             * - STATUS_ON broadcast is sent by OrbotService. However, the localBroadcastReceiver of the TileService is already unregistered and can't receive status update.
             * -> Therefore STATUS_STARTING must also activated the Tile or unregistration must be delayed. However starting can take quite some time, so delay must be large enough.
             */
            //case STATUS_STARTING:
            case STATUS_ON:
                System.out.println("--------------------------------------STATUS ON -> TURN STATE_ACTIVE-----------------------------------");
                getQsTile().setState(Tile.STATE_ACTIVE);
                break;
            case STATUS_OFF:
                System.out.println("--------------------------------------STATUS OFF -> TURN STATE_INACTIVE-----------------------------------");
                getQsTile().setState(Tile.STATE_INACTIVE);
                break;
            case STATUS_STARTING:
            case STATUS_STOPPING:
                getQsTile().setState(Tile.STATE_UNAVAILABLE);
                break;
        }
        getQsTile().updateTile();
    }

    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            System.out.println("--------------------------------------onReceive1() status " + intent.getStringExtra(TorServiceConstants.EXTRA_STATUS) + "-----------------------------------");

            if (action.equals(TorServiceConstants.ACTION_STATUS)) {
                System.out.println(intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                String status = intent.getStringExtra(TorServiceConstants.EXTRA_STATUS);
                System.out.println("--------------------------------------onReceive2() status " + status + "-----------------------------------");
                updateTile(status);
            }

            /*
            if (action == null)
                return;

            switch (action) {
                case TorServiceConstants.LOCAL_ACTION_LOG: {
                    System.out.println(intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                    break;
                }
                case TorServiceConstants.LOCAL_ACTION_BANDWIDTH: {
                    System.out.println(intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                    break;
                }
                case TorServiceConstants.ACTION_STATUS: {
                    System.out.println(intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                    status = intent.getStringExtra(TorServiceConstants.EXTRA_STATUS);
                    updateTile(status);
                    break;
                }
                case TorServiceConstants.LOCAL_ACTION_PORTS: {
                    System.out.println(intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                    break;
                }
                case ACTION_STOP_VPN: {
                    System.out.println(intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                    break;
                }
            }*/
        }
    };
}
