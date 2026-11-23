package com.minjun.healthbridge.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/** 앱 화면을 띄우고 탭 3개를 모두 그려도 죽지 않는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityLaunchTest {
    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test fun launches_and_renders_every_tab() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { act ->
                val vm = ViewModelProvider(act)[ImportViewModel::class.java]
                for (t in ImportViewModel.Tab.entries) {
                    vm.selectTab(t)
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEquals(t, vm.tab.value)
                }
                vm.showError("테스트 오류")
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }
}
