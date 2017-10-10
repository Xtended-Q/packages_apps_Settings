/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.settings.development;

import android.content.Context;
import android.os.UserManager;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

public class BugReportPreferenceControllerV2 extends DeveloperOptionsPreferenceController implements
        PreferenceControllerMixin {

    private static final String KEY_BUGREPORT = "bugreport";

    private final UserManager mUserManager;

    public BugReportPreferenceControllerV2(Context context) {
        super(context);

        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public boolean isAvailable() {
        return !mUserManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES);
    }

    @Override
    public String getPreferenceKey() {
        return KEY_BUGREPORT;
    }

    @Override
    protected void onDeveloperOptionsSwitchEnabled() {
        // intentional no-op
    }

    @Override
    protected void onDeveloperOptionsSwitchDisabled() {
        // intentional no-op
    }
}
