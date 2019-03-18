package com.example.ubercloneapp

import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.rengwuxian.materialedittext.MaterialEditText
import dmax.dialog.SpotsDialog
import kotlinx.android.synthetic.main.activity_main.*
import uk.co.chrisjenx.calligraphy.CalligraphyConfig
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper

class MainActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mDatabase: FirebaseDatabase
    private lateinit var users: DatabaseReference

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CalligraphyConfig.initDefault(CalligraphyConfig.Builder()
            .setDefaultFontPath("font/arkhip_font.ttf")
            .setFontAttrId(R.attr.fontPath)
            .build())
        setContentView(R.layout.activity_main)

        //Init firebase
        mAuth = FirebaseAuth.getInstance()
        mDatabase = FirebaseDatabase.getInstance()
        users = mDatabase.reference

        btnRegister.setOnClickListener {
            showRegisterDialog()
        }

        btnSignIn.setOnClickListener {
            showLoginDialog()
        }
    }

    private fun showLoginDialog() {
        var dialog = AlertDialog.Builder(this)
        dialog.setTitle("Login")
        dialog.setMessage("Please enter email to Login")

        val inflater = LayoutInflater.from(this)
        val loginLayout = inflater.inflate(R.layout.layout_login, null)

        val edtEmail = loginLayout.findViewById<MaterialEditText>(R.id.edtEmail)
        val edtPassword = loginLayout.findViewById<MaterialEditText>(R.id.edtPassword)
        dialog.setView(loginLayout)
        dialog.setPositiveButton("LOGIN"){dialogInterface, i ->
            if (edtEmail.text.isNullOrEmpty()) {
                Snackbar.make(rootLayout, "Please enter email address", Snackbar.LENGTH_SHORT).show()
            } else if (edtPassword.text.isNullOrEmpty()) {
                Snackbar.make(rootLayout, "Please enter password", Snackbar.LENGTH_SHORT).show()
            } else if (edtPassword.text.toString().length < 6){
                Snackbar.make(rootLayout,"Password too short", Snackbar.LENGTH_SHORT).show()
            } else {
                val waitingDialog = SpotsDialog.Builder()
                    .setContext(this@MainActivity)
                    .setMessage("Loading")
                    .setCancelable(false)
                    .build().apply {
                        show()
                    }
                mAuth.signInWithEmailAndPassword(edtEmail.text.toString(), edtPassword.text.toString())
                    .addOnSuccessListener {
                        waitingDialog.dismiss()
                        startActivity(Intent(this@MainActivity, Welcome::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        waitingDialog.dismiss()
                        Snackbar.make(rootLayout, "Login failed ${it.message}", Snackbar.LENGTH_SHORT).show()
                    }
            }
        }
        dialog.setNegativeButton("CANCEL"){dialog: DialogInterface?, which: Int ->
            dialog?.dismiss()
        }
        dialog.show()
    }

    private fun showRegisterDialog() {
        var dialog = AlertDialog.Builder(this)
        dialog.setTitle("Register ")
        dialog.setMessage("Please enter email to Register")

        val inflater = LayoutInflater.from(this)
        val registerLayout = inflater.inflate(R.layout.layout_register, null)

        val edtEmail = registerLayout.findViewById<MaterialEditText>(R.id.edtEmail)
        val edtPassword = registerLayout.findViewById<MaterialEditText>(R.id.edtPassword)
        val edtName = registerLayout.findViewById<MaterialEditText>(R.id.edtName)
        val edtPhone = registerLayout.findViewById<MaterialEditText>(R.id.edtPhone)

        dialog.setView(registerLayout)

        //Set button
        dialog.setPositiveButton("REGISTER") { dialogInterface, i ->
            dialogInterface.dismiss()
            if (edtEmail.text.isNullOrEmpty()) {
                Snackbar.make(rootLayout, "Please enter email address", Snackbar.LENGTH_SHORT).show()
            } else if (edtPhone.text.isNullOrEmpty()) {
                Snackbar.make(rootLayout, "Please enter phone number", Snackbar.LENGTH_SHORT).show()
            } else if (edtPassword.text.isNullOrEmpty()) {
                Snackbar.make(rootLayout, "Please enter password", Snackbar.LENGTH_SHORT).show()
            } else if (edtPassword.text.toString().length < 6) {
                Snackbar.make(rootLayout, "Password too short", Snackbar.LENGTH_SHORT).show()
            } else {
                mAuth.createUserWithEmailAndPassword(edtEmail.text.toString(), edtPassword.text.toString()).addOnSuccessListener {
                    val user = User(edtEmail.text.toString(),
                        edtPassword.text.toString(),
                        edtName.text.toString(),
                        edtPhone.text.toString())

                    // Use email to key
                    users.child(FirebaseAuth.getInstance().currentUser!!.uid)
                        .setValue(user)
                        .addOnSuccessListener {
                            Snackbar.make(rootLayout, "Register Successfully", Snackbar.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            Snackbar.make(rootLayout, "Register failed ${it.message}", Snackbar.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener {
                    Snackbar.make(rootLayout, "Register failed ${it.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        dialog.setNegativeButton("CANCEL"){dialogInterface, i ->
            dialogInterface.dismiss()
        }

        dialog.show()
    }
}
