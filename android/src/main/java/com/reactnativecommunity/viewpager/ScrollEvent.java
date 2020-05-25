package com.reactnativecommunity.viewpager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

/**
 * Event emitted by {@link ReactViewPager} when the ViewPager is scrolled (by the user or by the
 * view itself).
 *
 * Additional data provided by this event:
 *  - scrollX
 *  - oldScrollX
 *  - scrollY
 *  - oldScrollY
 */

/* package */ class ScrollEvent extends Event<ScrollEvent> {
    public static final String EVENT_NAME = "viewScroll";

    private final int mScrollX;
    private final int mScrollY;
    private final int mOldScrollX;
    private final int mOldScrollY;

    protected ScrollEvent(int viewTag, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        super(viewTag);
        mScrollX = scrollX;
        mScrollY = scrollY;
        mOldScrollX = oldScrollX;
        mOldScrollY = oldScrollY;
    }

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public void dispatch(RCTEventEmitter rctEventEmitter) {
        rctEventEmitter.receiveEvent(getViewTag(), getEventName(), serializeEventData());
    }

    private WritableMap serializeEventData() {
        WritableMap eventData = Arguments.createMap();
        eventData.putInt("scrollX", mScrollX);
        eventData.putInt("scrollY", mScrollY);
        eventData.putInt("oldScrollX", mOldScrollX);
        eventData.putInt("oldScrollY", mOldScrollY);
        return eventData;
    }

}
