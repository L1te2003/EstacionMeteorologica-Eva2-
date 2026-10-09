package com.lunasazo.estacionmeteorologica

import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    companion object {
        // Ajusta estos umbrales según tus sensores
        private const val UMBRAL_TEMPERATURA = 40.0 // °C
        private const val UMBRAL_AGUA = 20.0        // cm
    }

    private lateinit var tvTemperatura: TextView
    private lateinit var tvHumedad: TextView
    private lateinit var tvAgua: TextView
    private lateinit var swSirena: CompoundButton
    private lateinit var database: DatabaseReference

    private var listener: ValueEventListener? = null
    private var actualizandoDesdeFirebase = false
    private var alertaMostrada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Control de acceso: sin sesión se vuelve al login
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_dashboard)

        tvTemperatura = findViewById(R.id.tvTemperatura)
        tvHumedad = findViewById(R.id.tvHumedad)
        tvAgua = findViewById(R.id.tvAgua)
        swSirena = findViewById(R.id.swSirena)

        // Conexión al nodo de sensores en Realtime Database
        database = FirebaseDatabase.getInstance().reference.child("sensores")

        // El usuario mueve el switch -> se escribe en Firebase
        swSirena.setOnCheckedChangeListener { _, isChecked ->
            if (!actualizandoDesdeFirebase) {
                database.child("sirena").setValue(isChecked)
                    .addOnFailureListener {
                        Toast.makeText(this, "No se pudo enviar el comando", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::database.isInitialized) return // no hay sesión, onCreate ya redirigió

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = (snapshot.child("temperatura").value as? Number)?.toDouble()
                val hum = (snapshot.child("humedad").value as? Number)?.toDouble()
                val agua = (snapshot.child("nivel_agua").value as? Number)?.toDouble()
                val sirena = snapshot.child("sirena").value as? Boolean ?: false

                tvTemperatura.text = temp?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "-- °C"
                tvHumedad.text = hum?.let { String.format(Locale.getDefault(), "%.0f %%", it) } ?: "-- %"
                tvAgua.text = agua?.let { String.format(Locale.getDefault(), "%.1f cm", it) } ?: "-- cm"

                // Evita que este cambio dispare una escritura de vuelta
                actualizandoDesdeFirebase = true
                swSirena.isChecked = sirena
                actualizandoDesdeFirebase = false

                // Alerta automática: se abre una sola vez hasta que los valores vuelvan a la normalidad
                val fueraDeLimite =
                    (temp != null && temp >= UMBRAL_TEMPERATURA) ||
                            (agua != null && agua >= UMBRAL_AGUA)

                if (fueraDeLimite && !alertaMostrada) {
                    alertaMostrada = true
                    startActivity(Intent(this@DashboardActivity, AlertaActivity::class.java))
                } else if (!fueraDeLimite) {
                    alertaMostrada = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@DashboardActivity,
                    "Error de Firebase: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        database.addValueEventListener(listener!!)
    }

    override fun onStop() {
        super.onStop()
        listener?.let { database.removeEventListener(it) }
        listener = null
    }
}