package com.example.steamapp

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup


class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var steamIdAdapter: ArrayAdapter<String>
    private lateinit var steamIdAutoCompleteTextView: AutoCompleteTextView
    private lateinit var gamesCountTextView: TextView
    private lateinit var badgesCountTextView: TextView
    private lateinit var avatarCardView : CardView
    private lateinit var steamUrl : String
    private lateinit var openProfileButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("SteamApp", Context.MODE_PRIVATE)
        steamIdAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)

        val savedIds = sharedPreferences.getStringSet("saved_steam_ids", setOf()) ?: setOf()
        openProfileButton = findViewById(R.id.openProfileButton)
        openProfileButton.setVisibility(View.GONE)

        steamIdAutoCompleteTextView = findViewById(R.id.steamIdAutoCompleteTextView)
        val fetchDataButton: Button = findViewById(R.id.fetchDataButton)
        val clearSavedIdsButton: Button = findViewById(R.id.clearSavedIdsButton)
        gamesCountTextView = findViewById(R.id.gamesCountTextView)
        badgesCountTextView = findViewById(R.id.badgesCountTextView)

        avatarCardView = findViewById(R.id.avatarCardView)
        avatarCardView.setVisibility(View.GONE);

        steamIdAutoCompleteTextView.setAdapter(steamIdAdapter)

        fetchDataButton.setOnClickListener {
            val steamId = steamIdAutoCompleteTextView.text.toString()
            if (steamId.isNotBlank()) {
                fetchData(steamId)
            }
        }

        clearSavedIdsButton.setOnClickListener {
            showClearConfirmationDialog()
        }

        openProfileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(steamUrl))
            startActivity(intent)
        }

        steamIdAdapter.addAll(savedIds)
        steamIdAdapter.notifyDataSetChanged()
    }

    private fun fetchData(steamId: String) {
        val savedIds = (0 until steamIdAdapter.count).mapNotNull { steamIdAdapter.getItem(it) }.toSet()
        if (savedIds.contains(steamId)) {
            fetchDataFromSteam(steamId)
        } else {
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        fetchDataFromSteam(steamId)
                        saveSteamId(steamId)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        fetchDataFromSteam(steamId)
                    }
                }
            }

            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setMessage("Цей SteamID ще не був у пошуку. \nХочете зберегти його?\nПісля збереження він буде доступний для автозаповнення.")
                .setPositiveButton("Так", dialogClickListener)
                .setNegativeButton("Ні", dialogClickListener)
                .show()
        }
    }


    private fun fetchDataFromSteam(steamId: String) {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                steamUrl = "https://steamcommunity.com/profiles/$steamId"
                val document = Jsoup.connect(steamUrl).get()
                val name = document.select(".actual_persona_name").text()
                val friendsCount = document.select("a[href*='/friends'] span:nth-child(2)").text()
                val level = document.select("a[href*='/badges'] > div > div > span").text()
                val avatarUrl = document.select(".playerAvatarAutoSizeInner > img").attr("src")
                val gamesCount = document.select("a[href*='/games/?tab=all'] span:nth-child(2)").text()
                val groupsCount = document.select("a[href*='/groups/'] span:nth-child(2)").text()
                val badgesCount = document.select("a[href*='/badges/'] span:nth-child(2)").text()
                steamIdAutoCompleteTextView.text = null

                withContext(Dispatchers.Main) {
                    if (name.isEmpty() && level.isEmpty()) {
                        displayError()
                    }else if(name.isNotEmpty() && level.isEmpty()){
                        displayData(name + "\n(прихований профіль)", "приховано", "приховано", avatarUrl, "приховано", "приховано", "приховано")
                    }else if(name.isNotEmpty() && level.isNotEmpty() && friendsCount.isEmpty()){
                        displayData(name, "приховано", level, avatarUrl, gamesCount, groupsCount, badgesCount)
                    }

                    else {
                        displayData(name, friendsCount, level, avatarUrl, gamesCount, groupsCount, badgesCount)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    displayError()
                }
            }
        }
    }

    private fun displayData(name: String, friendsCount: String, level: String, avatarUrl: String, gamesCount: String, groupsCount: String, badgesCount: String) {
        val nameTextView: TextView = findViewById(R.id.nameTextView)
        val friendsCountTextView: TextView = findViewById(R.id.friendsCountTextView)
        val levelTextView: TextView = findViewById(R.id.levelTextView)
        val avatarImageView: ImageView = findViewById(R.id.avatarImageView)
        val groupsCountTextView: TextView = findViewById(R.id.groupsCountTextView)

        nameTextView.text = "Ім'я: $name"
        friendsCountTextView.text = "Кі-сть друзів: $friendsCount"
        levelTextView.text = "Рівень: $level"
        gamesCountTextView.text = "Кі-сть ігор: $gamesCount"
        groupsCountTextView.text = "Кі-сть груп: $groupsCount"
        badgesCountTextView.text = "Кі-сть значків: $badgesCount"


        Glide.with(this)
            .load(avatarUrl)
            .into(avatarImageView)

        avatarCardView.setVisibility(View.VISIBLE);
        openProfileButton.setVisibility(View.VISIBLE);
    }

    private fun displayError() {
        val nameTextView: TextView = findViewById(R.id.nameTextView)
        val friendsCountTextView: TextView = findViewById(R.id.friendsCountTextView)
        val levelTextView: TextView = findViewById(R.id.levelTextView)
        val avatarImageView: ImageView = findViewById(R.id.avatarImageView)
        val groupsCountTextView: TextView = findViewById(R.id.groupsCountTextView)

        nameTextView.text = "Сталася помилка в отриманні даних\nПеревірте коректність даних"
        friendsCountTextView.text = ""
        levelTextView.text = ""
        gamesCountTextView.text = ""
        groupsCountTextView.text = ""
        badgesCountTextView.text = ""

        Glide.with(this)
            .load(R.drawable.error_image)
            .into(avatarImageView)

        avatarCardView.setVisibility(View.VISIBLE);
        openProfileButton.setVisibility(View.GONE);
    }

    private fun showClearConfirmationDialog() {
        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> clearSavedSteamIds()
            }
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setMessage("Хочет видалити SteamIDs?")
            .setPositiveButton("Так", dialogClickListener)
            .setNegativeButton("Ні", dialogClickListener)
            .show()
    }

    private fun clearSavedSteamIds() {
        steamIdAdapter.clear()
        steamIdAdapter.notifyDataSetChanged()

        val editor = sharedPreferences.edit()
        editor.remove("steam_ids")
        editor.remove("saved_steam_ids")
        editor.apply()
    }

    private fun saveSteamId(steamId: String) {
        val savedIds = sharedPreferences.getStringSet("saved_steam_ids", setOf()) ?: setOf()
        if (!savedIds.contains(steamId)) {
            val updatedIds = savedIds.toMutableSet()
            updatedIds.add(steamId)

            val editor = sharedPreferences.edit()
            editor.putStringSet("saved_steam_ids", updatedIds)
            editor.apply()

            steamIdAdapter.add(steamId)
            steamIdAdapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_saved_ids -> {
                showClearConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}



