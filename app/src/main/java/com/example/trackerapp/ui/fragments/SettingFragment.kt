package com.example.trackerapp.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.trackerapp.R
import com.example.trackerapp.other.CONST
import com.example.trackerapp.other.CONST.KEY_NAME
import com.example.trackerapp.other.CONST.KEY_WEIGHT
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_setting.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : Fragment(R.layout.fragment_setting) {
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadFieldFromSharedPref()
        btnApplyChanges.setOnClickListener {
            val success = applyChangesToSharedPref()
            if (success) {
                Snackbar.make(view,"Saved changes",Snackbar.LENGTH_LONG)
                    .show()
            } else {
                Snackbar.make(view, "Please enter all the fields", Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun loadFieldFromSharedPref(){
        val name = sharedPreferences.getString(KEY_NAME,"")
        val weight = sharedPreferences.getFloat(KEY_WEIGHT,80F)
        etName.setText(name)
        etWeight.setText(weight.toString())
    }
    private fun applyChangesToSharedPref(): Boolean {
        val nameTxt = etName.text.toString()
        val weightTxt = etWeight.text.toString()
        if (nameTxt.isEmpty() || weightTxt.isEmpty()) {
            return false
        }
        sharedPreferences.edit()
            .putString(CONST.KEY_NAME, nameTxt)
            .putFloat(CONST.KEY_WEIGHT, weightTxt.toFloat())
            .apply()
        val toolbarText = "Let's go, $nameTxt"
        requireActivity().tvToolbarTitle.text = toolbarText
        return true
    }
}