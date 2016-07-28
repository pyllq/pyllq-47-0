/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.firstrun;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.mozilla.gecko.R;
import org.mozilla.gecko.Telemetry;
import org.mozilla.gecko.TelemetryContract;
import org.mozilla.gecko.fxa.FxAccountConstants;
import org.mozilla.gecko.fxa.activities.FxAccountWebFlowActivity;
import org.mozilla.gecko.PrefsHelper;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

public class PyllqOptionsPanel extends FirstrunPanel  {
    private static final String LOGTAG = "PyllqOptionsPanel";

    private static final int DIALOGID_WHATSNEW = 1;

    public static final int PYLLQ_OPTION_LANG = 1;
    public static final int PYLLQ_OPTION_ZOOM = 2;
    public static final int PYLLQ_OPTION_CHARACTER = 3;
    public static final int PYLLQ_OPTION_PINYIN = 4;
    public static final int PYLLQ_OPTION_LOCATION = 5;
    public static final int PYLLQ_OPTION_GRADE = 6;
    public static final int PYLLQ_OPTION_FIRST = 7;
    public static final int PYLLQ_OPTION_LAST = 8;

    public static final int TITLE_RES_CHARACTER = R.string.langEncoding;
    public static final int TITLE_RES_PINYIN = R.string.pinyinFormat;
    public static final int TITLE_RES_LOCATION = R.string.pinyinLocation;
    public static final int TITLE_RES_GRADE = R.string.grade;
    public static final int TITLE_RES_FIRST = R.string.firstrun_firstpage;
    public static final int TITLE_RES_LAST = R.string.firstrun_lastpage;
    

    private int pyllqOption = -1;

    private ViewGroup root = null;

    // make this global
    static private int mPinyinEnc = 0;
    static private int mPinyinFormat = 0;
    static private int mPinyinLocation = 0;
    static private int mPinyinGrade = 0;

    private void setSelectionImage() {
        if (pyllqOption == PYLLQ_OPTION_GRADE) {
            ImageView vGradeImg = (ImageView) root.findViewById(R.id.img_grade);

            switch(mPinyinGrade) {
            case 0: vGradeImg.setImageResource(R.drawable.fp_grade_a); break;
            case 2: vGradeImg.setImageResource(R.drawable.fp_grade_b); break;
            case 12: vGradeImg.setImageResource(R.drawable.fp_grade_c); break;
            }

            return;
        }

        if (pyllqOption != PYLLQ_OPTION_CHARACTER &&
            pyllqOption != PYLLQ_OPTION_PINYIN &&
            pyllqOption != PYLLQ_OPTION_LOCATION) return;

        ImageView vRubyTop = (ImageView) root.findViewById(R.id.ruby_top);
        ImageView vRubyBottom = (ImageView) root.findViewById(R.id.ruby_bottom);
        ImageView vEncText = (ImageView) root.findViewById(R.id.enc_text);

        switch(mPinyinEnc) {
            case 0: // R.id.radio_simplified
                vEncText.setImageResource(R.drawable.fp_simp); break;
            case 1: // R.id.radio_traditional
                vEncText.setImageResource(R.drawable.fp_trad); break;
            case 2: // R.id.radio_original
                vEncText.setImageResource(R.drawable.fp_simp); break;
        }
        if (mPinyinLocation == 0) {
            // R.id.radio_top
            vRubyTop.setVisibility(View.VISIBLE);
            vRubyBottom.setVisibility(View.INVISIBLE);
            switch(mPinyinFormat) {
                case 0: // R.id.radio_hypy
                    vRubyTop.setImageResource(R.drawable.fp_hypy); break;
                case 4: // R.id.radio_hypy_num
                    vRubyTop.setImageResource(R.drawable.fp_hypy_num); break;
                case 8: // R.id.radio_bpmf
                    vRubyTop.setImageResource(R.drawable.fp_bpmf); break;
                case 12: // R.id.radio_bpmf_num
                    vRubyTop.setImageResource(R.drawable.fp_bpmf_num); break;
            }
        }
        else {
            // R.id.radio_bottom
            // R.id.radio_top
            vRubyTop.setVisibility(View.INVISIBLE);
            vRubyBottom.setVisibility(View.VISIBLE);
            switch(mPinyinFormat) {
                case 0: // R.id.radio_hypy
                    vRubyBottom.setImageResource(R.drawable.fp_hypy); break;
                case 4: // R.id.radio_hypy_num
                    vRubyBottom.setImageResource(R.drawable.fp_hypy_num); break;
                case 8: // R.id.radio_bpmf
                    vRubyBottom.setImageResource(R.drawable.fp_bpmf); break;
                case 12: // R.id.radio_bpmf_num
                    vRubyBottom.setImageResource(R.drawable.fp_bpmf_num); break;
            }
        }
    }

    private void setRadioChecks() {
        if (pyllqOption == PYLLQ_OPTION_GRADE) {
            switch(mPinyinGrade) {
            case 0:
                ((RadioButton)root.findViewById(R.id.radio_gradeA)).setChecked(true);
                ((TextView)root.findViewById(R.id.text_desc_grade)).setText(R.string.gradeADesc);
                break;
            case 2:
                ((RadioButton)root.findViewById(R.id.radio_gradeB)).setChecked(true);
                ((TextView)root.findViewById(R.id.text_desc_grade)).setText(R.string.gradeBDesc);
                break;
            case 12:
                ((RadioButton)root.findViewById(R.id.radio_gradeC)).setChecked(true);
                ((TextView)root.findViewById(R.id.text_desc_grade)).setText(R.string.gradeCDesc);
                break;
            }
            return;
        }

        switch(mPinyinEnc) {
        case 0: ((RadioButton)root.findViewById(R.id.radio_simplified)).setChecked(true); break;          
        case 1: ((RadioButton)root.findViewById(R.id.radio_traditional)).setChecked(true); break;          
        case 2: ((RadioButton)root.findViewById(R.id.radio_original)).setChecked(true); break;
        }

        switch(mPinyinFormat) {
        case 0: ((RadioButton)root.findViewById(R.id.radio_hanyuPinyin)).setChecked(true); break;
        case 4: ((RadioButton)root.findViewById(R.id.radio_hanyuPinyinNum)).setChecked(true); break;
        case 8: ((RadioButton)root.findViewById(R.id.radio_bpmf)).setChecked(true); break;
        case 12: ((RadioButton)root.findViewById(R.id.radio_bpmfNum)).setChecked(true); break;
        }

        switch(mPinyinLocation) {
        case 0: ((RadioButton)root.findViewById(R.id.radio_top)).setChecked(true); break;
        case 16: ((RadioButton)root.findViewById(R.id.radio_bottom)).setChecked(true); break;
        }
    }

    private void setOptions(int id) {

        if (id == R.id.radio_simplified) {
            PrefsHelper.setPref("pyllq.langEncoding", "0");
            mPinyinEnc = 0;
        }
        else if (id == R.id.radio_traditional) {
            PrefsHelper.setPref("pyllq.langEncoding", "1");
            mPinyinEnc = 1;
        }
        else if (id == R.id.radio_original) {
            PrefsHelper.setPref("pyllq.langEncoding", "2");
            mPinyinEnc = 2;
        }
        else if (id == R.id.radio_hanyuPinyin) {
            PrefsHelper.setPref("pyllq.pinyinFormat", "0");
            mPinyinFormat = 0;
        }
        else if (id == R.id.radio_hanyuPinyinNum) {
            PrefsHelper.setPref("pyllq.pinyinFormat", "4");
            mPinyinFormat = 4;
        }
        else if (id == R.id.radio_bpmf) {
            PrefsHelper.setPref("pyllq.pinyinFormat", "8");
            mPinyinFormat = 8;
        }
        else if (id == R.id.radio_bpmfNum) {
            PrefsHelper.setPref("pyllq.pinyinFormat", "12");
            mPinyinFormat = 12;
        }
        else if (id == R.id.radio_top) {
            PrefsHelper.setPref("pyllq.pinyinLocation", "0");
            mPinyinLocation = 0;
        }
        else if (id == R.id.radio_bottom) {
            PrefsHelper.setPref("pyllq.pinyinLocation", "16");
            mPinyinLocation = 16;
        }
        else if (id == R.id.radio_gradeA) {
            PrefsHelper.setPref("pyllq.grade", "0");
            mPinyinGrade = 0;
            ((TextView)root.findViewById(R.id.text_desc_grade)).setText(R.string.gradeADesc);
        }
        else if (id == R.id.radio_gradeB) {
            PrefsHelper.setPref("pyllq.grade", "2");
            mPinyinGrade = 2;
            ((TextView)root.findViewById(R.id.text_desc_grade)).setText(R.string.gradeBDesc);
        }
        else if (id == R.id.radio_gradeC) {
            PrefsHelper.setPref("pyllq.grade", "12");
            mPinyinGrade = 12;
            ((TextView)root.findViewById(R.id.text_desc_grade)).setText(R.string.gradeCDesc);
        }

        setSelectionImage();        
    }

    @Override
    public void onSelected() {
        Log.d(LOGTAG,"onSelected:"+pyllqOption);
        setSelectionImage();
    }

    private void initPrefs() {
        final String[] prefs = {
                        "pyllq.langEncoding",
                        "pyllq.pinyinFormat",
                        "pyllq.pinyinLocation",
                        "pyllq.grade"
                    };

        PrefsHelper.getPrefs(prefs, new PrefsHelper.PrefHandlerBase() {

            @Override public void prefValue(String pref, String value) {
                Log.d(LOGTAG,"prefsString:"+pref+":"+value);
                if (pref == "pyllq.langEncoding") mPinyinEnc = Integer.parseInt(value);
                else if (pref == "pyllq.pinyinFormat") mPinyinFormat = Integer.parseInt(value);
                else if (pref == "pyllq.pinyinLocation") mPinyinLocation = Integer.parseInt(value);
                else if (pref == "pyllq.grade") mPinyinGrade = Integer.parseInt(value);
            }
            @Override public void finish() {
                Log.d(LOGTAG,"completed prefs");
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        setSelectionImage();
                        setRadioChecks();
                    }
                });
            }
        });
    }

    private void showWhatsNewDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(R.string.firstrun_whatsnew_title);
        alertDialog.setMessage(R.string.firstrun_whatsnew_text);
        alertDialog.setPositiveButton(R.string.firstrun_whatsnew_close, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        alertDialog.show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        Bundle args = getArguments();
        if (args != null) {
            pyllqOption = args.getInt(FirstrunPagerConfig.KEY_IMAGE);
        }

        if (pyllqOption == PYLLQ_OPTION_ZOOM)
            root = null;
        else if (pyllqOption == PYLLQ_OPTION_CHARACTER) {
            root = (ViewGroup) inflater.inflate(R.layout.firstrun_pyllq_enc_fragment, container, false);
            initPrefs();
            ((TextView)root.findViewById(R.id.text_desc)).setText(R.string.langEncodingDesc);
        }
        else if (pyllqOption == PYLLQ_OPTION_PINYIN) {
            root = (ViewGroup) inflater.inflate(R.layout.firstrun_pyllq_enc_fragment, container, false);
            initPrefs();
            ((TextView)root.findViewById(R.id.text_desc)).setText(R.string.pinyinFormatDesc);
        }
        else if (pyllqOption == PYLLQ_OPTION_LOCATION) {
            root = (ViewGroup) inflater.inflate(R.layout.firstrun_pyllq_enc_fragment, container, false);
            initPrefs();
            ((TextView)root.findViewById(R.id.text_desc)).setText(R.string.pinyinLocationDesc);
        }
        else if (pyllqOption == PYLLQ_OPTION_GRADE) {
            root = (ViewGroup) inflater.inflate(R.layout.firstrun_pyllq_grade_fragment, container, false);
            ((TextView)root.findViewById(R.id.text_desc_grade)).setVisibility(View.VISIBLE);
            initPrefs();
        }
        else if (pyllqOption == PYLLQ_OPTION_FIRST)
            root = (ViewGroup) inflater.inflate(R.layout.firstrun_pyllq_first_fragment, container, false);
        else if (pyllqOption == PYLLQ_OPTION_LAST)
            root = (ViewGroup) inflater.inflate(R.layout.firstrun_pyllq_last_fragment, container, false);
        
        View v = root.findViewById(R.id.welcome_browse);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.BUTTON, "firstrun-browser");
                    close();
                }
            });
        }

        v = root.findViewById(R.id.firstrun_link);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.BUTTON, "firstrun-next");
                    pagerNavigation.next();
                }
            });
        }

        v = root.findViewById(R.id.firstrun_whatsnew);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWhatsNewDialog();
                }
            });
        }

        v = root.findViewById(R.id.radio_group_enc);
        if (v != null && pyllqOption == PYLLQ_OPTION_CHARACTER) {
            RadioGroup radioGroup = (RadioGroup) v;
            radioGroup.setVisibility(View.VISIBLE);
            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    setOptions(checkedId);
                }
            });
        }

        v = root.findViewById(R.id.radio_group_pinyin);
        if (v != null && pyllqOption == PYLLQ_OPTION_PINYIN) {
            RadioGroup radioGroup = (RadioGroup) v;
            radioGroup.setVisibility(View.VISIBLE);
            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    setOptions(checkedId);
                }
            });
        }

        v = root.findViewById(R.id.radio_group_location);
        if (v != null && pyllqOption == PYLLQ_OPTION_LOCATION) {
            RadioGroup radioGroup = (RadioGroup) v;
            radioGroup.setVisibility(View.VISIBLE);
            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    setOptions(checkedId);
                }
            });
        }

        v = root.findViewById(R.id.radio_group_grade);
        if (v != null) {
            RadioGroup radioGroup = (RadioGroup) v;
            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    setOptions(checkedId);
                }
            });
        }

        return root;
    }

}
