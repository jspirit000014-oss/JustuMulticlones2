package top.niunaijun.blackboxa.view.proxy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import top.niunaijun.blackboxa.data.ProxyRepository
import top.niunaijun.blackboxa.view.base.BaseActivity
import java.util.UUID

class ProxyManagerActivity : BaseActivity() {

    private lateinit var repo: ProxyRepository
    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getIntExtra("userID", 0)
        repo = ProxyRepository(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Shield Privacidad - Clon $userId"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }

        val proxyTitle = TextView(this).apply { text = "Proxy IP:Puerto" }
        val etHost = EditText(this).apply { hint = "Ej: 192.168.1.1" }
        val etPort = EditText(this).apply { hint = "Ej: 8080"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        val btnProxy = Button(this).apply { text = "Guardar Proxy" }
        btnProxy.setOnClickListener {
            val host = etHost.text.toString()
            val port = etPort.text.toString().toIntOrNull() ?: 0
            if (host.isNotEmpty() && port > 0) {
                repo.setProxy(userId, host, port)
                Toast.makeText(this, "Proxy guardado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ingresa host y puerto validos", Toast.LENGTH_SHORT).show()
            }
        }

        val btnClearProxy = Button(this).apply { text = "Quitar Proxy" }
        btnClearProxy.setOnClickListener {
            repo.clearProxy(userId)
            etHost.setText("")
            etPort.setText("")
            Toast.makeText(this, "Proxy eliminado", Toast.LENGTH_SHORT).show()
        }

        val spoofTitle = TextView(this).apply {
            text = "Cambiar Identidad del Dispositivo"
            setPadding(0, 32, 0, 0)
        }
        val etAndroidId = EditText(this).apply { hint = "Android ID (vacio = aleatorio)" }
        val etModel = EditText(this).apply { hint = "Modelo (Ej: Samsung Galaxy S21)" }

        val btnSpoof = Button(this).apply { text = "Aplicar Identidad" }
        btnSpoof.setOnClickListener {
            val androidId = etAndroidId.text.toString().ifEmpty { UUID.randomUUID().toString().replace("-", "").take(16) }
            val model = etModel.text.toString().ifEmpty { "Xiaomi Redmi Note 10" }
            repo.spoofDevice(userId, androidId, model)
            Toast.makeText(this, "Identidad: $androidId", Toast.LENGTH_LONG).show()
        }

        val clearTitle = TextView(this).apply {
            text = "Limpiar Rastro"
            setPadding(0, 32, 0, 0)
        }
        val btnClear = Button(this).apply {
            text = "Borrar Cache y Datos del Clon"
            setBackgroundColor(android.graphics.Color.parseColor("#FF5252"))
            setTextColor(android.graphics.Color.WHITE)
        }
        btnClear.setOnClickListener {
            repo.clearAllData(userId, this)
            Toast.makeText(this, "Datos borrados del clon $userId", Toast.LENGTH_SHORT).show()
        }

        val btnShow = Button(this).apply { text = "Ver Configuracion Actual" }
        btnShow.setOnClickListener {
            val proxy = repo.getProxy(userId)
            val device = repo.getSpoofedDevice(userId)
            val msg = "Proxy: ${proxy?.first ?: "Ninguno"}:${proxy?.second ?: ""}\nAndroid ID: ${device?.first ?: "Original"}\nModelo: ${device?.second ?: "Original"}"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        layout.addView(title)
        layout.addView(proxyTitle)
        layout.addView(etHost)
        layout.addView(etPort)
        layout.addView(btnProxy)
        layout.addView(btnClearProxy)
        layout.addView(spoofTitle)
        layout.addView(etAndroidId)
        layout.addView(etModel)
        layout.addView(btnSpoof)
        layout.addView(clearTitle)
        layout.addView(btnClear)
        layout.addView(btnShow)

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }

    companion object {
        fun start(context: Context, userId: Int) {
            val intent = Intent(context, ProxyManagerActivity::class.java)
            intent.putExtra("userID", userId)
            context.startActivity(intent)
        }
    }
}
