package com.wmdhs.shorea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            ShoreA()
        }
    }
}

@Composable
private fun ShoreA() {
    val context = LocalContext.current
    val darkTheme = (context.resources.configuration.uiMode and 0x30) == 0x20

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ExpressiveHome()
        }
    }
}

@Composable
private fun ExpressiveHome() {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Material 3 Compose",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = if (expanded) "收起内容" else "展开内容"
            )
        }

        AnimatedVisibility(visible = expanded) {
            Card(
                modifier = Modifier.padding(top = 24.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Text(
                    text = "Termux Compose 开发环境已经成功运行。",
                    modifier = Modifier.padding(28.dp),
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }
        }
    }
}
