package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.example.model.FormFactorState
import com.example.model.FormFactorType
import com.example.service.KineticSensorService
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sensorService by mutableStateOf<KineticSensorService?>(null)
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? KineticSensorService.LocalBinder
            sensorService = binder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sensorService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        // Bind to KineticSensorService
        val serviceIntent = Intent(this, KineticSensorService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Monitor dynamic fold posture via Jetpack WindowManager
        lifecycleScope.launch {
            WindowInfoTracker.getOrCreate(this@MainActivity)
                .windowLayoutInfo(this@MainActivity)
                .collectLatest { layoutInfo ->
                    val foldingFeature = layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()

                    val formFactor = if (foldingFeature != null) {
                        val isFlexMode = foldingFeature.state == FoldingFeature.State.HALF_OPENED
                        val isVerticalHinge = foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL

                        when {
                            isVerticalHinge && isFlexMode -> FormFactorState(
                                type = FormFactorType.FOLDABLE_UNFOLDED,
                                hingeAngleDegrees = 90f,
                                isFoldableDevice = true
                            )
                            isVerticalHinge -> FormFactorState(
                                type = FormFactorType.FOLDABLE_UNFOLDED,
                                hingeAngleDegrees = 180f,
                                isFoldableDevice = true
                            )
                            isFlexMode -> FormFactorState(
                                type = FormFactorType.FLIP_HALF_OPEN,
                                hingeAngleDegrees = 90f,
                                isFoldableDevice = true
                            )
                            else -> FormFactorState(
                                type = FormFactorType.FLIP_OPEN,
                                hingeAngleDegrees = 180f,
                                isFoldableDevice = true
                            )
                        }
                    } else {
                        // Standard candybar smartphone
                        FormFactorState(
                            type = FormFactorType.CANDYBAR,
                            isFoldableDevice = false
                        )
                    }

                    sensorService?.updateFormFactor(formFactor)
                }
        }

        setContent {
            MyApplicationTheme {
                val currentService = sensorService ?: KineticSensorService.instance

                if (currentService != null) {
                    DashboardScreen(service = currentService)
                } else {
                    LoadingScreen()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

@Composable
fun LoadingScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
