/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.reactnativecommunity.viewpager;

import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.uimanager.events.NativeGestureUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper view for {@link ViewPager}. It's forwarding calls to {@link ViewGroup#addView} to add
 * views to custom {@link PagerAdapter} instance which is used by {@link NativeViewHierarchyManager}
 * to add children nodes according to react views hierarchy.
 */
public class ReactViewPager extends ViewPager {

  private class Adapter extends PagerAdapter {

    private final List<View> mViews = new ArrayList<>();
    private boolean mIsViewPagerInIntentionallyInconsistentState = false;

    void addView(View child, int index) {
      mViews.add(index, child);
      notifyDataSetChanged();
    }

    void removeViewAt(int index) {
      mViews.remove(index);
      notifyDataSetChanged();
    }

    /**
     * Replace a set of views to the ViewPager adapter and update the ViewPager
     */
    void setViews(List<View> views) {
      mViews.clear();
      mViews.addAll(views);
      notifyDataSetChanged();

      // we want to make sure we return POSITION_NONE for every view here, since this is only
      // called after a removeAllViewsFromAdapter
      mIsViewPagerInIntentionallyInconsistentState = false;
    }

    /**
     * Remove all the views from the adapter and de-parents them from the ViewPager
     * After calling this, it is expected that notifyDataSetChanged should be called soon
     * afterwards.
     */
    void removeAllViewsFromAdapter(ViewPager pager) {
      mViews.clear();
      pager.removeAllViews();
      // set this, so that when the next addViews is called, we return POSITION_NONE for every
      // entry so we can remove whichever views we need to and add the ones that we need to.
      mIsViewPagerInIntentionallyInconsistentState = true;
    }

    View getViewAt(int index) {
      return mViews.get(index);
    }

    @Override
    public int getCount() {
      return mViews.size();
    }

    @Override
    public int getItemPosition(Object object) {
      // if we've removed all views, we want to return POSITION_NONE intentionally
      return mIsViewPagerInIntentionallyInconsistentState || !mViews.contains(object) ?
        POSITION_NONE : mViews.indexOf(object);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
      View view = mViews.get(position);
      container.addView(view, 0, generateDefaultLayoutParams());
      post(measureAndLayout);
      return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
      container.removeView((View) object);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
      return view == object;
    }
  }

  private class PageChangeListener implements ViewPager.OnPageChangeListener {

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      mEventDispatcher.dispatchEvent(
          new PageScrollEvent(getId(), position, positionOffset));
    }

    @Override
    public void onPageSelected(int position) {
      Log.wtf("ReactViewPager", "onPageScrolled: " + position);
      if (position == 0) setAllowedSwipeDirection(SwipeDirection.right);
      else if (position == getViewCountInAdapter() - 1) setAllowedSwipeDirection(SwipeDirection.left);
      else setAllowedSwipeDirection(SwipeDirection.all);
      if (!mIsCurrentItemFromJs) {
        mEventDispatcher.dispatchEvent(
            new PageSelectedEvent(getId(), position));
      }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
      String pageScrollState;
      switch (state) {
        case SCROLL_STATE_IDLE:
          pageScrollState = "idle";
          break;
        case SCROLL_STATE_DRAGGING:
          pageScrollState = "dragging";
          break;
        case SCROLL_STATE_SETTLING:
          pageScrollState = "settling";
          break;
        default:
          throw new IllegalStateException("Unsupported pageScrollState");
      }
      mEventDispatcher.dispatchEvent(
        new PageScrollStateChangedEvent(getId(), pageScrollState));
    }
  }

  private final EventDispatcher mEventDispatcher;
  private boolean mIsCurrentItemFromJs;
  private boolean mScrollEnabled = true;
  private float initialXValue;
  private SwipeDirection direction;

  public ReactViewPager(ReactContext reactContext) {
    super(reactContext);
    this.direction = SwipeDirection.all;
    mEventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    mIsCurrentItemFromJs = false;
    setOnPageChangeListener(new PageChangeListener());
    setAdapter(new Adapter());
  }

  @Override
  public Adapter getAdapter() {
    return (Adapter) super.getAdapter();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (!isSwipeAllowed(ev)) {
      //Log.wtf("ReactViewPager", "onInterceptTouchEvent swipe not allowed");
      return false;
    };
    try {
      if (super.onInterceptTouchEvent(ev)) {
        NativeGestureUtil.notifyNativeGestureStarted(this, ev);
        return true;
      }
    } catch (IllegalArgumentException e) {
      // Log and ignore the error. This seems to be a bug in the android SDK and
      // this is the commonly accepted workaround.
      // https://tinyurl.com/mw6qkod (Stack Overflow)
      FLog.w(ReactConstants.TAG, "Error intercepting touch event.", e);
    }

    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    if (!isSwipeAllowed(ev)) {
      //Log.wtf("ReactViewPager", "onTouchEvent swipe not allowed");
      return false;
    };

    try {
      return super.onTouchEvent(ev);
    } catch (IllegalArgumentException e) {
      // Log and ignore the error. This seems to be a bug in the android SDK and
      // this is the commonly accepted workaround.
      // https://fburl.com/5d3iw7d9
      FLog.w(ReactConstants.TAG, "Error handling touch event.", e);
    }

    return false;
  }

  private boolean isSwipeAllowed(MotionEvent event) {
    if (!mScrollEnabled) return false;
    if(this.direction == SwipeDirection.all) return true;

    if(direction == SwipeDirection.none )//disable any swipe
      return false;

    if(event.getAction()==MotionEvent.ACTION_DOWN) {
      initialXValue = event.getX();
      return true;
    }

    if(event.getAction()==MotionEvent.ACTION_MOVE) {
      try {
        float diffX = event.getX() - initialXValue;
        if (diffX > 0 && direction == SwipeDirection.right ) {
          // swipe from left to right detected
          return false;
        }else if (diffX < 0 && direction == SwipeDirection.left ) {
          // swipe from right to left detected
          return false;
        }
      } catch (Exception exception) {
        exception.printStackTrace();
      }
    }

    return true;
  }

  public void setAllowedSwipeDirection(SwipeDirection direction) {
    this.direction = direction;
  }

  public void setCurrentItemFromJs(int item, boolean animated) {
    mIsCurrentItemFromJs = true;
    setCurrentItem(item, animated);
    mIsCurrentItemFromJs = false;
  }

  public void setScrollEnabled(boolean scrollEnabled) {
    mScrollEnabled = scrollEnabled;
  }


  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    // The viewpager reset an internal flag on this method so we need to run another layout pass
    // after attaching to window.
    this.requestLayout();
    post(measureAndLayout);
  }

  private final Runnable measureAndLayout = new Runnable() {
    @Override
    public void run() {
      measure(
              MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
      layout(getLeft(), getTop(), getRight(), getBottom());
    }
  };

  /*package*/ void addViewToAdapter(View child, int index) {
    getAdapter().addView(child, index);
  }

  /*package*/ void removeViewFromAdapter(int index) {
    getAdapter().removeViewAt(index);
  }

  /*package*/ int getViewCountInAdapter() {
    return getAdapter().getCount();
  }

  /*package*/ View getViewFromAdapter(int index) {
    return getAdapter().getViewAt(index);
  }

  public void setViews(List<View> views) {
    getAdapter().setViews(views);
  }

  public void removeAllViewsFromAdapter() {
    getAdapter().removeAllViewsFromAdapter(this);
  }
}
