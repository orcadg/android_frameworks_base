/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.internal.widget;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewRoot;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import com.android.internal.R;

public class PasswordEntryKeyboardHelper implements OnKeyboardActionListener {

    public static final int KEYBOARD_MODE_ALPHA = 0;
    public static final int KEYBOARD_MODE_NUMERIC = 1;
    private static final int KEYBOARD_STATE_NORMAL = 0;
    private static final int KEYBOARD_STATE_SHIFTED = 1;
    private static final int KEYBOARD_STATE_CAPSLOCK = 2;
    private static final String TAG = "PasswordEntryKeyboardHelper";
    private int mKeyboardMode = KEYBOARD_MODE_ALPHA;
    private int mKeyboardState = KEYBOARD_STATE_NORMAL;
    private PasswordEntryKeyboard mQwertyKeyboard;
    private PasswordEntryKeyboard mQwertyKeyboardShifted;
    private PasswordEntryKeyboard mSymbolsKeyboard;
    private PasswordEntryKeyboard mSymbolsKeyboardShifted;
    private PasswordEntryKeyboard mNumericKeyboard;
    private Context mContext;
    private View mTargetView;
    private KeyboardView mKeyboardView;

    public PasswordEntryKeyboardHelper(Context context, KeyboardView keyboardView, View targetView) {
        mContext = context;
        mTargetView = targetView;
        mKeyboardView = keyboardView;
        createKeyboards();
        mKeyboardView.setOnKeyboardActionListener(this);
    }

    public boolean isAlpha() {
        return mKeyboardMode == KEYBOARD_MODE_ALPHA;
    }

    private void createKeyboards() {
        mNumericKeyboard = new PasswordEntryKeyboard(mContext, R.xml.password_kbd_numeric);
        mQwertyKeyboard = new PasswordEntryKeyboard(mContext,
                R.xml.password_kbd_qwerty, R.id.mode_normal);
        mQwertyKeyboard.enableShiftLock();

        mQwertyKeyboardShifted = new PasswordEntryKeyboard(mContext,
                R.xml.password_kbd_qwerty_shifted,
                R.id.mode_normal);
        mQwertyKeyboardShifted.enableShiftLock();
        mQwertyKeyboardShifted.setShifted(true); // always shifted.

        mSymbolsKeyboard = new PasswordEntryKeyboard(mContext, R.xml.password_kbd_symbols);
        mSymbolsKeyboard.enableShiftLock();

        mSymbolsKeyboardShifted = new PasswordEntryKeyboard(mContext,
                R.xml.password_kbd_symbols_shift);
        mSymbolsKeyboardShifted.enableShiftLock();
        mSymbolsKeyboardShifted.setShifted(true); // always shifted
    }

    public void setKeyboardMode(int mode) {
        switch (mode) {
            case KEYBOARD_MODE_ALPHA:
                mKeyboardView.setKeyboard(mQwertyKeyboard);
                mKeyboardState = KEYBOARD_STATE_NORMAL;
                break;
            case KEYBOARD_MODE_NUMERIC:
                mKeyboardView.setKeyboard(mNumericKeyboard);
                mKeyboardState = KEYBOARD_STATE_NORMAL;
                break;
        }
        mKeyboardMode = mode;
    }

    private void sendKeyEventsToTarget(int keyEventCode) {
        Handler handler = mTargetView.getHandler();
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.ALPHA).getEvents(
                new char[] { (char) keyEventCode });
        if (events != null) {
            for (KeyEvent event : events) {
                handler.sendMessage(handler.obtainMessage(ViewRoot.DISPATCH_KEY, event));
            }
        }
    }

    public void sendDownUpKeyEvents(int keyEventCode) {
        long eventTime = SystemClock.uptimeMillis();
        Handler handler = mTargetView.getHandler();
        handler.sendMessage(handler.obtainMessage(ViewRoot.DISPATCH_KEY_FROM_IME,
                new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, 0, 0, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE)));
        handler.sendMessage(handler.obtainMessage(ViewRoot.DISPATCH_KEY_FROM_IME,
                new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, 0, 0, 0,
                        KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE)));
    }

    public void onKey(int primaryCode, int[] keyCodes) {
        Log.v(TAG, "Key code = " + Integer.toHexString(primaryCode));
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mKeyboardView != null) {
            handleModeChange();
        } else {
            handleCharacter(primaryCode, keyCodes);
            // Switch back to old keyboard if we're not in capslock mode
            if (mKeyboardState == KEYBOARD_STATE_SHIFTED) {
                // skip to the unlocked state
                mKeyboardState = KEYBOARD_STATE_CAPSLOCK;
                handleShift();
            }
        }
    }

    private void handleModeChange() {
        final Keyboard current = mKeyboardView.getKeyboard();
        Keyboard next = null;
        if (current == mQwertyKeyboard || current == mQwertyKeyboardShifted) {
            next = mSymbolsKeyboard;
        } else if (current == mSymbolsKeyboard || current == mSymbolsKeyboardShifted) {
            next = mQwertyKeyboard;
        }
        if (next != null) {
            mKeyboardView.setKeyboard(next);
            mKeyboardState = KEYBOARD_STATE_NORMAL;
        }
    }

    private void handleBackspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void handleShift() {
        if (mKeyboardView == null) {
            return;
        }
        Keyboard current = mKeyboardView.getKeyboard();
        PasswordEntryKeyboard next = null;
        final boolean isAlphaMode = current == mQwertyKeyboard
                || current == mQwertyKeyboardShifted;
        if (mKeyboardState == KEYBOARD_STATE_NORMAL) {
            mKeyboardState = isAlphaMode ? KEYBOARD_STATE_SHIFTED : KEYBOARD_STATE_CAPSLOCK;
            next = isAlphaMode ? mQwertyKeyboardShifted : mSymbolsKeyboardShifted;
        } else if (mKeyboardState == KEYBOARD_STATE_SHIFTED) {
            mKeyboardState = KEYBOARD_STATE_CAPSLOCK;
            next = isAlphaMode ? mQwertyKeyboardShifted : mSymbolsKeyboardShifted;
        } else if (mKeyboardState == KEYBOARD_STATE_CAPSLOCK) {
            mKeyboardState = KEYBOARD_STATE_NORMAL;
            next = isAlphaMode ? mQwertyKeyboard : mSymbolsKeyboard;
        }
        if (next != null) {
            if (next != current) {
                mKeyboardView.setKeyboard(next);
            }
            next.setShiftLocked(mKeyboardState == KEYBOARD_STATE_CAPSLOCK);
            mKeyboardView.setShifted(mKeyboardState != KEYBOARD_STATE_NORMAL);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        // Maybe turn off shift if not in capslock mode.
        if (mKeyboardView.isShifted() && primaryCode != ' ' && primaryCode != '\n') {
            primaryCode = Character.toUpperCase(primaryCode);
        }
        sendKeyEventsToTarget(primaryCode);
    }

    private void handleClose() {

    }

    public void onPress(int primaryCode) {

    }

    public void onRelease(int primaryCode) {

    }

    public void onText(CharSequence text) {

    }

    public void swipeDown() {

    }

    public void swipeLeft() {

    }

    public void swipeRight() {

    }

    public void swipeUp() {

    }
};
