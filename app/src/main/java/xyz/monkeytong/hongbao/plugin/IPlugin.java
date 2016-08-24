package xyz.monkeytong.hongbao.plugin;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import xyz.monkeytong.hongbao.utils.HongbaoSignature;

/**
 * plugin interface
 * Created by cabe on 16/8/23.
 */
public interface IPlugin {
    void setService(AccessibilityService service);
    void setSignature(HongbaoSignature signature);
    void setSharedPreferences(SharedPreferences sp);
    void setCurrentActivity(String activityName);
    void watchChat(AccessibilityEvent event);
    boolean watchList(AccessibilityEvent event);
    boolean watchNotifications(AccessibilityEvent event);
    AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node);
    void checkNodeInfo(int eventType);
    void handleEvent(AccessibilityEvent event);
}
