package xyz.monkeytong.hongbao.plugin;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import xyz.monkeytong.hongbao.utils.HongbaoSignature;

/**
 * weChat Plugin
 * Created by cabe on 16/8/23.
 */
public class WeWorkPlugin implements IPlugin {
    private final static String TAG = "WeWorkPlugin";
    public final static String PLUGIN_PACKAGE_NAME = "com.tencent.wework";

    private static final String WE_WORK_DETAILS_CH = "企业微信红包";
    private static final String WE_WORK_BETTER_LUCK_CH = "手慢了，红包派完了";
    private static final String WE_WORK_EXPIRES_CH = "已超过24小时";
    private static final String WE_WORK_VIEW_SELF_CH = "查看红包";
    private static final String WE_WORK_VIEW_OTHERS_CH = "领取红包";
    private static final String WE_WORK_NOTIFICATION_TIP = "[拼手气红包]";
    private static final String WE_WORK_LUCK_MONEY_RECEIVE_ACTIVITY = "RedEnvelopeCollectorActivity";
    private static final String WE_WORK_LUCK_MONEY_DETAIL_ACTIVITY = "RedEnvelopeDetailActivity";
    private static final String WE_WORK_LUCK_MONEY_GENERAL_ACTIVITY = "WwMainActivity";
    private static final String WE_WORK_LUCK_MONEY_CHATTING_ACTIVITY = "MessageListActivity";
    private String currentActivityName = WE_WORK_LUCK_MONEY_GENERAL_ACTIVITY;

    private AccessibilityNodeInfo rootNodeInfo, mReceiveNode, mUnpackNode;
    private boolean mLuckyMoneyPicked, mLuckyMoneyReceived;
    private int mUnpackCount = 0;
    private boolean mMutex = false, mListMutex = false, mChatMutex = false;
    private HongbaoSignature signature;

    private AccessibilityService service;
    private SharedPreferences sharedPreferences;

    @Override
    public void setService(AccessibilityService service) {
        this.service = service;
    }

    @Override
    public void setSignature(HongbaoSignature signature) {
        this.signature = signature;
    }

    @Override
    public void setSharedPreferences(SharedPreferences sp) {
        this.sharedPreferences = sp;
    }

    @Override
    public void setCurrentActivity(String activityName) {
        this.currentActivityName = activityName;
    }

    public void watchChat(AccessibilityEvent event) {
        this.rootNodeInfo = service.getRootInActiveWindow();

        if (rootNodeInfo == null) return;

        mReceiveNode = null;
        mUnpackNode = null;

        checkNodeInfo(event.getEventType());

        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && !mLuckyMoneyPicked && (mReceiveNode != null)) {
            mMutex = true;

            mReceiveNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            mLuckyMoneyReceived = false;
            mLuckyMoneyPicked = true;
        }
        /* 如果戳开但还未领取 */
        if (mUnpackCount == 1 && (mUnpackNode != null)) {
            int delayFlag = sharedPreferences.getInt("pref_open_delay", 0) * 1000;
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            try {
                                mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            } catch (Exception e) {
                                mMutex = false;
                                mLuckyMoneyPicked = false;
                                mUnpackCount = 0;
                            }
                        }
                    },
                    delayFlag);
        }
    }

    public boolean watchList(AccessibilityEvent event) {
        if (mListMutex) return false;
        mListMutex = true;
        AccessibilityNodeInfo eventSource = event.getSource();
        // Not a message
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventSource == null)
            return false;

        List<AccessibilityNodeInfo> nodes = eventSource.findAccessibilityNodeInfosByText(WE_WORK_NOTIFICATION_TIP);
        //增加条件判断currentActivityName.contains(WE_WORK_LUCK_MONEY_GENERAL_ACTIVITY)
        //避免当订阅号中出现标题为“[微信红包]拜年红包”（其实并非红包）的信息时误判
        if (!nodes.isEmpty() && currentActivityName.contains(WE_WORK_LUCK_MONEY_GENERAL_ACTIVITY)) {
            AccessibilityNodeInfo nodeToClick = nodes.get(0);
            if (nodeToClick == null) return false;
            CharSequence contentDescription = nodeToClick.getText();
            if (contentDescription != null && !signature.getContentDescription().equals(contentDescription)) {
                nodeToClick.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                signature.setContentDescription(contentDescription.toString());
                return true;
            }
        }
        return false;
    }

    public boolean watchNotifications(AccessibilityEvent event) {
        // Not a notification
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
            return false;

        // Not a hongbao
        String tip = event.getText().toString();
        if (!tip.contains(WE_WORK_NOTIFICATION_TIP)) return true;

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                /* 清除signature,避免进入会话后误判 */
                signature.cleanSignature();

                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node) {
        if (node == null)
            return null;

        //非layout元素
        if (node.getChildCount() == 0) {
            if ("android.widget.ImageView".equals(node.getClassName())) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                int screenWidth = service.getResources().getDisplayMetrics().widthPixels;
                int marginLeft = bounds.left;
                int marginRight = screenWidth - bounds.right;
                return Math.abs(marginLeft - marginRight) > 100 ? null : node;
            } else {
                return null;
            }
        }

        //layout元素，遍历找button
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if(childNode != null) {
                List<AccessibilityNodeInfo> nodeList = childNode.findAccessibilityNodeInfosByText(WE_WORK_BETTER_LUCK_CH);
                if(nodeList != null && !nodeList.isEmpty()) {
                    return childNode;
                }
                AccessibilityNodeInfo button = findOpenButton(childNode);
                if (button != null)
                    return button;
            }
        }
        return null;
    }

    private boolean isDetailActivity(AccessibilityNodeInfo node) {
        if (node == null)
            return false;

        //非layout元素
        if (node.getChildCount() == 0) {
            String text = node.getText() + "";
            return text.equals(WE_WORK_DETAILS_CH);
        }

        //layout元素，遍历找button
        for (int i = 0; i < node.getChildCount(); i++) {
            boolean isDetail = isDetailActivity(node.getChild(i));
            if (isDetail)
                return true;
        }
        return false;
    }

    public void checkNodeInfo(int eventType) {
        if (this.rootNodeInfo == null) return;

        if (signature.commentString != null) {
            sendComment();
            signature.commentString = null;
        }

        /* 聊天会话窗口，遍历节点匹配“领取红包”和"查看红包" */
        AccessibilityNodeInfo node1 = (sharedPreferences.getBoolean("pref_watch_self", false))
                ? getTheLastNode(WE_WORK_VIEW_OTHERS_CH, WE_WORK_VIEW_SELF_CH)
                : getTheLastNode(WE_WORK_VIEW_OTHERS_CH);
        boolean containActivity = currentActivityName.contains(WE_WORK_LUCK_MONEY_CHATTING_ACTIVITY)
                || currentActivityName.contains(WE_WORK_LUCK_MONEY_GENERAL_ACTIVITY);
        if (node1 != null && containActivity) {
            String excludeWords = sharedPreferences.getString("pref_watch_exclude_words", "");
            if (signature.generateSignature(node1, excludeWords, PLUGIN_PACKAGE_NAME)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = node1;
                Log.d(TAG, signature.toString());
            }
            return;
        }

        /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” */
        AccessibilityNodeInfo node2 = findOpenButton(rootNodeInfo);
        if (node2 != null && currentActivityName.contains(WE_WORK_LUCK_MONEY_RECEIVE_ACTIVITY)) {
            mUnpackNode = node2;
            mUnpackCount += 1;
            return;
        }

        boolean isStateChanged = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        boolean isDetail = isDetailActivity(rootNodeInfo);
        /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了” */
        boolean hasNodes = hasOneOfThoseNodes(WE_WORK_BETTER_LUCK_CH, WE_WORK_DETAILS_CH, WE_WORK_EXPIRES_CH);
        boolean isPacketActivity = currentActivityName.contains(WE_WORK_LUCK_MONEY_DETAIL_ACTIVITY)
                || currentActivityName.contains(WE_WORK_LUCK_MONEY_RECEIVE_ACTIVITY);
        if (isStateChanged && isDetail && hasNodes && isPacketActivity) {
            mMutex = false;
            mLuckyMoneyPicked = false;
            mUnpackCount = 0;
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            signature.commentString = generateCommentString();
        }
    }

    private void sendComment() {
        try {
            AccessibilityNodeInfo outNode = service.getRootInActiveWindow().getChild(0).getChild(0);
            AccessibilityNodeInfo nodeToInput = outNode.getChild(outNode.getChildCount() - 1).getChild(0).getChild(1);

            if ("android.widget.EditText".equals(nodeToInput.getClassName())) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, signature.commentString);
                nodeToInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private boolean hasOneOfThoseNodes(String... texts) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : texts) {
            if (text == null) continue;

            nodes = rootNodeInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) return null;

                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
                    signature.others = text.equals(WE_WORK_VIEW_OTHERS_CH);
                }
            }
        }
        return lastNode;
    }

    private String generateCommentString() {
        if (!signature.others) return null;

        Boolean needComment = sharedPreferences.getBoolean("pref_comment_switch", false);
        if (!needComment) return null;

        String[] wordsArray = sharedPreferences.getString("pref_comment_words", "").split(" +");
        if (wordsArray.length == 0) return null;

        Boolean atSender = sharedPreferences.getBoolean("pref_comment_at", false);
        if (atSender) {
            return "@" + signature.sender + " " + wordsArray[(int) (Math.random() * wordsArray.length)];
        } else {
            return wordsArray[(int) (Math.random() * wordsArray.length)];
        }
    }

    @Override
    public void handleEvent(AccessibilityEvent event) {
        if(service == null || signature == null || sharedPreferences == null) return;

        /* 检测通知消息 */
        if (!mMutex) {
            if (sharedPreferences.getBoolean("pref_watch_notification", false) && watchNotifications(event)) return;
            if (sharedPreferences.getBoolean("pref_watch_list", false) && watchList(event)) return;
            mListMutex = false;
        }

        if (!mChatMutex) {
            mChatMutex = true;
            if (sharedPreferences.getBoolean("pref_watch_chat", false)) watchChat(event);
            mChatMutex = false;
        }
    }
}
