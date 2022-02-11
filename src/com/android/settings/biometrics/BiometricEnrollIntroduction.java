/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.settings.biometrics;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.hardware.biometrics.BiometricAuthenticator;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SetupWizardUtils;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.password.SetupSkipDialog;

import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.span.LinkSpan;
import com.google.android.setupdesign.template.RequireScrollMixin;
import com.google.android.setupdesign.util.DynamicColorPalette;

/**
 * Abstract base class for the intro onboarding activity for biometric enrollment.
 */
public abstract class BiometricEnrollIntroduction extends BiometricEnrollBase
        implements LinkSpan.OnClickListener {

    private static final String TAG = "BiometricEnrollIntroduction";

    private static final String KEY_CONFIRMING_CREDENTIALS = "confirming_credentials";

    private UserManager mUserManager;
    protected boolean mHasPassword;
    private boolean mBiometricUnlockDisabledByAdmin;
    private TextView mErrorText;
    protected boolean mConfirmingCredentials;
    protected boolean mNextClicked;
    private boolean mParentalConsentRequired;

    @Nullable private PorterDuffColorFilter mIconColorFilter;

    /**
     * @return true if the biometric is disabled by a device administrator
     */
    protected abstract boolean isDisabledByAdmin();

    /**
     * @return the layout resource
     */
    protected abstract int getLayoutResource();

    /**
     * @return the header resource for if the biometric has been disabled by a device administrator
     */
    protected abstract int getHeaderResDisabledByAdmin();

    /**
     * @return the default header resource
     */
    protected abstract int getHeaderResDefault();

    /**
     * @return the description resource for if the biometric has been disabled by a device admin
     */
    protected abstract int getDescriptionResDisabledByAdmin();

    /**
     * @return the cancel button
     */
    protected abstract FooterButton getCancelButton();

    /**
     * @return the next button
     */
    protected abstract FooterButton getNextButton();

    /**
     * @return the error TextView
     */
    protected abstract TextView getErrorTextView();

    /**
     * @return 0 if there are no errors, otherwise returns the resource ID for the error string
     * to be displayed.
     */
    protected abstract int checkMaxEnrolled();

    /**
     * @return the challenge generated by the biometric hardware
     */
    protected abstract void getChallenge(GenerateChallengeCallback callback);

    /**
     * @return one of the ChooseLockSettingsHelper#EXTRA_KEY_FOR_* constants
     */
    protected abstract String getExtraKeyForBiometric();

    /**
     * @return the intent for proceeding to the next step of enrollment. For Fingerprint, this
     * should lead to the "Find Sensor" activity. For Face, this should lead to the "Enrolling"
     * activity.
     */
    protected abstract Intent getEnrollingIntent();

    /**
     * @return the title to be shown on the ConfirmLock screen.
     */
    protected abstract int getConfirmLockTitleResId();

    /**
     * @param span
     */
    public abstract void onClick(LinkSpan span);

    public abstract @BiometricAuthenticator.Modality int getModality();

    protected interface GenerateChallengeCallback {
        void onChallengeGenerated(int sensorId, int userId, long challenge);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mConfirmingCredentials = savedInstanceState.getBoolean(KEY_CONFIRMING_CREDENTIALS);
        }

        Intent intent = getIntent();
        if (intent.getStringExtra(WizardManagerHelper.EXTRA_THEME) == null) {
            // Put the theme in the intent so it gets propagated to other activities in the flow
            intent.putExtra(
                    WizardManagerHelper.EXTRA_THEME,
                    SetupWizardUtils.getThemeString(intent));
        }

        mBiometricUnlockDisabledByAdmin = isDisabledByAdmin();

        setContentView(getLayoutResource());
        mParentalConsentRequired = ParentalControlsUtils.parentConsentRequired(this, getModality())
                != null;
        if (mBiometricUnlockDisabledByAdmin && !mParentalConsentRequired) {
            setHeaderText(getHeaderResDisabledByAdmin());
        } else {
            setHeaderText(getHeaderResDefault());
        }

        mErrorText = getErrorTextView();

        mUserManager = UserManager.get(this);
        updatePasswordQuality();

        if (!mConfirmingCredentials) {
            if (!mHasPassword) {
                // No password registered, launch into enrollment wizard.
                mConfirmingCredentials = true;
                launchChooseLock();
            } else if (!BiometricUtils.containsGatekeeperPasswordHandle(getIntent())
                    && mToken == null) {
                // It's possible to have a token but mLaunchedConfirmLock == false, since
                // ChooseLockGeneric can pass us a token.
                mConfirmingCredentials = true;
                launchConfirmLock(getConfirmLockTitleResId());
            }
        }

        final GlifLayout layout = getLayout();
        mFooterBarMixin = layout.getMixin(FooterBarMixin.class);
        mFooterBarMixin.setPrimaryButton(getPrimaryFooterButton());
        mFooterBarMixin.setSecondaryButton(getSecondaryFooterButton(), true /* usePrimaryStyle */);
        mFooterBarMixin.getSecondaryButton().setVisibility(View.INVISIBLE);

        final RequireScrollMixin requireScrollMixin = layout.getMixin(RequireScrollMixin.class);
        requireScrollMixin.requireScrollWithButton(this, getPrimaryFooterButton(),
                getMoreButtonTextRes(), this::onNextButtonClick);
        requireScrollMixin.setOnRequireScrollStateChangedListener(
                scrollNeeded -> {
                    // Update text of primary button from "More" to "Agree".
                    final int primaryButtonTextRes = scrollNeeded
                            ? getMoreButtonTextRes()
                            : getAgreeButtonTextRes();
                    getPrimaryFooterButton().setText(this, primaryButtonTextRes);

                    // Show secondary button once scroll is completed.
                    if (!scrollNeeded) {
                        getSecondaryFooterButton().setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        final int errorMsg = checkMaxEnrolled();
        if (errorMsg == 0) {
            mErrorText.setText(null);
            mErrorText.setVisibility(View.GONE);
            getNextButton().setVisibility(View.VISIBLE);
        } else {
            mErrorText.setText(errorMsg);
            mErrorText.setVisibility(View.VISIBLE);
            getNextButton().setText(getResources().getString(R.string.done));
            getNextButton().setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_CONFIRMING_CREDENTIALS, mConfirmingCredentials);
    }

    @Override
    protected boolean shouldFinishWhenBackgrounded() {
        return super.shouldFinishWhenBackgrounded() && !mConfirmingCredentials && !mNextClicked;
    }

    private void updatePasswordQuality() {
        final int passwordQuality = new LockPatternUtils(this)
                .getActivePasswordQuality(mUserManager.getCredentialOwnerProfile(mUserId));
        mHasPassword = passwordQuality != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    }

    @Override
    protected void onNextButtonClick(View view) {
        mNextClicked = true;
        if (checkMaxEnrolled() == 0) {
            // Lock thingy is already set up, launch directly to the next page
            launchNextEnrollingActivity(mToken);
        } else {
            setResult(RESULT_FINISHED);
            finish();
        }
    }

    private void launchChooseLock() {
        Intent intent = BiometricUtils.getChooseLockIntent(this, getIntent());
        intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_INSECURE_OPTIONS, true);
        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW_HANDLE, true);
        intent.putExtra(getExtraKeyForBiometric(), true);
        if (mUserId != UserHandle.USER_NULL) {
            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        }
        startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
    }

    private void launchNextEnrollingActivity(byte[] token) {
        Intent intent = getEnrollingIntent();
        if (token != null) {
            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
        }
        if (mUserId != UserHandle.USER_NULL) {
            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        }
        BiometricUtils.copyMultiBiometricExtras(getIntent(), intent);
        intent.putExtra(EXTRA_FROM_SETTINGS_SUMMARY, mFromSettingsSummary);
        intent.putExtra(EXTRA_KEY_CHALLENGE, mChallenge);
        intent.putExtra(EXTRA_KEY_SENSOR_ID, mSensorId);
        startActivityForResult(intent, BIOMETRIC_FIND_SENSOR_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BIOMETRIC_FIND_SENSOR_REQUEST) {
            if (isResultSkipOrFinished(resultCode)) {
                handleBiometricResultSkipOrFinished(resultCode, data);
            } else if (resultCode == RESULT_TIMEOUT) {
                setResult(resultCode, data);
                finish();
            }
        } else if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST) {
            mConfirmingCredentials = false;
            if (resultCode == RESULT_FINISHED) {
                updatePasswordQuality();
                final boolean handled = onSetOrConfirmCredentials(data);
                if (!handled) {
                    overridePendingTransition(R.anim.sud_slide_next_in, R.anim.sud_slide_next_out);
                    getNextButton().setEnabled(false);
                    getChallenge(((sensorId, userId, challenge) -> {
                        mSensorId = sensorId;
                        mChallenge = challenge;
                        mToken = BiometricUtils.requestGatekeeperHat(this, data, mUserId,
                                challenge);
                        BiometricUtils.removeGatekeeperPasswordHandle(this, data);
                        getNextButton().setEnabled(true);
                    }));
                }
            } else {
                setResult(resultCode, data);
                finish();
            }
        } else if (requestCode == CONFIRM_REQUEST) {
            mConfirmingCredentials = false;
            if (resultCode == RESULT_OK && data != null) {
                final boolean handled = onSetOrConfirmCredentials(data);
                if (!handled) {
                    overridePendingTransition(R.anim.sud_slide_next_in, R.anim.sud_slide_next_out);
                    getNextButton().setEnabled(false);
                    getChallenge(((sensorId, userId, challenge) -> {
                        mSensorId = sensorId;
                        mChallenge = challenge;
                        mToken = BiometricUtils.requestGatekeeperHat(this, data, mUserId,
                                challenge);
                        BiometricUtils.removeGatekeeperPasswordHandle(this, data);
                        getNextButton().setEnabled(true);
                    }));
                }
            } else {
                setResult(resultCode, data);
                finish();
            }
        } else if (requestCode == LEARN_MORE_REQUEST) {
            overridePendingTransition(R.anim.sud_slide_back_in, R.anim.sud_slide_back_out);
        } else if (requestCode == ENROLL_NEXT_BIOMETRIC_REQUEST) {
            Log.d(TAG, "ENROLL_NEXT_BIOMETRIC_REQUEST, result: " + resultCode);
            if (isResultSkipOrFinished(resultCode)) {
                handleBiometricResultSkipOrFinished(resultCode, data);
            } else if (resultCode != RESULT_CANCELED) {
                setResult(resultCode, data);
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static boolean isResultSkipOrFinished(int resultCode) {
        return resultCode == RESULT_SKIP || resultCode == SetupSkipDialog.RESULT_SKIP
                || resultCode == RESULT_FINISHED;
    }

    private void handleBiometricResultSkipOrFinished(int resultCode, @Nullable Intent data) {
        if (data != null
                && data.getBooleanExtra(
                        MultiBiometricEnrollHelper.EXTRA_SKIP_PENDING_ENROLL, false)) {
            getIntent().removeExtra(MultiBiometricEnrollHelper.EXTRA_ENROLL_AFTER_FACE);
        }

        if (resultCode == RESULT_SKIP) {
            onEnrollmentSkipped(data);
        } else if (resultCode == RESULT_FINISHED) {
            onFinishedEnrolling(data);
        }
    }

    /**
     * Called after confirming credentials. Can be used to prevent the default
     * behavior of immediately calling #getChallenge (useful to things like intro
     * consent screens that don't actually do enrollment and will later start an
     * activity that does).
     *
     * @return True if the default behavior should be skipped and handled by this method instead.
     */
    protected boolean onSetOrConfirmCredentials(@Nullable Intent data) {
        return false;
    }

    protected void onCancelButtonClick(View view) {
        finish();
    }

    protected void onSkipButtonClick(View view) {
        onEnrollmentSkipped(null /* data */);
    }

    protected void onEnrollmentSkipped(@Nullable Intent data) {
        setResult(RESULT_SKIP, data);
        finish();
    }

    protected void onFinishedEnrolling(@Nullable Intent data) {
        setResult(RESULT_FINISHED, data);
        finish();
    }

    @Override
    protected void initViews() {
        super.initViews();

        if (mBiometricUnlockDisabledByAdmin && !mParentalConsentRequired) {
            setDescriptionText(getDescriptionResDisabledByAdmin());
        }
    }

    @NonNull
    protected PorterDuffColorFilter getIconColorFilter() {
        if (mIconColorFilter == null) {
            mIconColorFilter = new PorterDuffColorFilter(
                    DynamicColorPalette.getColor(this, DynamicColorPalette.ColorType.ACCENT),
                    PorterDuff.Mode.SRC_IN);
        }
        return mIconColorFilter;
    }

    @NonNull
    protected abstract FooterButton getPrimaryFooterButton();

    @NonNull
    protected abstract FooterButton getSecondaryFooterButton();

    @StringRes
    protected abstract int getAgreeButtonTextRes();

    @StringRes
    protected abstract int getMoreButtonTextRes();
}
