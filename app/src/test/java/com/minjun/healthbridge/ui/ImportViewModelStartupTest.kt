package com.minjun.healthbridge.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 앱을 켤 때 ViewModel이 만들어지다가 죽지 않는지 (v0.5.0~0.6.0 시작 충돌 회귀 방지) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportViewModelStartupTest {
    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test fun view_model_starts_without_crashing() {
        val vm = ImportViewModel(app)
        assertEquals(UiState.Pick, vm.state.value)
        assertEquals(ImportViewModel.Tab.Home, vm.tab.value)
    }
}
