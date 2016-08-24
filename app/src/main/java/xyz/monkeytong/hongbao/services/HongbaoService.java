package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import xyz.monkeytong.hongbao.plugin.IPlugin;
import xyz.monkeytong.hongbao.plugin.WeChatPlugin;
import xyz.monkeytong.hongbao.plugin.WeWorkPlugin;
import xyz.monkeytong.hongbao.utils.HongbaoSignature;
import xyz.monkeytong.hongbao.utils.PowerUtil;

public class HongbaoService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private String currentPackageName = "";
    private String currentActivityName = "";

    private PowerUtil powerUtil;
    private SharedPreferences sharedPreferences;
    private HongbaoSignature signature = new HongbaoSignature();

    @Override
    public void onInterrupt() {

    }

    /**
     * AccessibilityEvent
     *
     * @param event 事件
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (sharedPreferences == null) return;

        setCurrentActivityName(event);
        Log.w("HongbaoService", "onAccessibilityEvent:" + currentActivityName);

        IPlugin plugin = null;
        if(WeChatPlugin.PLUGIN_PACKAGE_NAME.equals(currentPackageName)) {
            plugin = new WeChatPlugin();
        } else if(WeWorkPlugin.PLUGIN_PACKAGE_NAME.equals(currentPackageName)) {
            plugin = new WeWorkPlugin();
        }

        if(plugin == null) return;

        plugin.setService(this);
        plugin.setSignature(signature);
        plugin.setSharedPreferences(sharedPreferences);
        plugin.setCurrentActivity(currentActivityName);
        plugin.handleEvent(event);
    }

    private void setCurrentActivityName(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        try {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );

            getPackageManager().getActivityInfo(componentName, 0);
            currentPackageName = componentName.getPackageName();
            currentActivityName = componentName.flattenToShortString();
        } catch (PackageManager.NameNotFoundException e) {
            currentActivityName = "";
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        this.watchFlagsFromPreference();
    }

    private void watchFlagsFromPreference() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        this.powerUtil = new PowerUtil(this);
        Boolean watchOnLockFlag = sharedPreferences.getBoolean("pref_watch_on_lock", false);
        this.powerUtil.handleWakeLock(watchOnLockFlag);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_watch_on_lock")) {
            Boolean changedValue = sharedPreferences.getBoolean(key, false);
            this.powerUtil.handleWakeLock(changedValue);
        }
    }

    @Override
    public void onDestroy() {
        this.powerUtil.handleWakeLock(false);
        super.onDestroy();
    }
}
