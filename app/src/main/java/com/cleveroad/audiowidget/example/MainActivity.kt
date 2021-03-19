package com.cleveroad.audiowidget.example

import android.Manifest
import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<Collection<MusicItem>>,
    SearchView.OnQueryTextListener {
    private var adapter: MusicAdapter? = null
    private var emptyViewObserver: EmptyViewObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val emptyView = findViewById<View>(R.id.empty_view)
        val filter = MusicFilter(ContextCompat.getColor(this, R.color.colorAccent))
        val adapter = MusicAdapter(this).also {
            this.adapter = it
        }
        adapter.withFilter(filter)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.adapter = adapter
        emptyViewObserver = EmptyViewObserver(emptyView).also {
            it.bind(recyclerView)
        }

        ItemClickSupport.addTo(recyclerView)
            .setOnItemClickListener(object : ItemClickSupport.OnItemClickListener {
                override fun onItemClick(
                    parent: RecyclerView?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val item = adapter.getItem(position)
                    if (!isServiceRunning(MusicService::class.java)) {
                        MusicService.setTracks(
                            this@MainActivity,
                            adapter.snapshot.toTypedArray()
                        )
                    }
                    MusicService.playTrack(this@MainActivity, item)
                }
            })

        // check if we can draw overlays
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
            val listener = DialogInterface.OnClickListener { dialog, which ->
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(
                            "package:$packageName"
                        )
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    onPermissionsNotGranted()
                }
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.permissions_title))
                .setMessage(getString(R.string.draw_over_permissions_message))
                .setPositiveButton(getString(R.string.btn_continue), listener)
                .setNegativeButton(getString(R.string.btn_cancel), listener)
                .setCancelable(false)
                .show()
            return
        }
        checkReadStoragePermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                onPermissionsNotGranted()
            } else {
                checkReadStoragePermission()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == EXT_STORAGE_PERMISSION_REQ_CODE) {
            for (i in permissions.indices) {
                if (Manifest.permission.READ_EXTERNAL_STORAGE == permissions[i] &&
                    grantResults[i] == PackageManager.PERMISSION_GRANTED
                ) {
                    loadMusic()
                    return
                }
            }
            onPermissionsNotGranted()
        }
    }

    override fun onResume() {
        super.onResume()
        MusicService.setState(this, false)
    }

    override fun onPause() {
        super.onPause()
        MusicService.setState(this, true)
    }

    /**
     * Check if we have necessary permissions.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun checkReadStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                val onClickListener = DialogInterface.OnClickListener { dialog, which ->
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            EXT_STORAGE_PERMISSION_REQ_CODE
                        )
                    } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                        onPermissionsNotGranted()
                    }
                    dialog.dismiss()
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_title)
                    .setMessage(R.string.read_ext_permissions_message)
                    .setPositiveButton(R.string.btn_continue, onClickListener)
                    .setNegativeButton(R.string.btn_cancel, onClickListener)
                    .setCancelable(false)
                    .show()
                return
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                EXT_STORAGE_PERMISSION_REQ_CODE
            )
            return
        }
        loadMusic()
    }

    /**
     * Load music.
     */
    private fun loadMusic() {
        LoaderManager.getInstance(this).initLoader(MUSIC_LOADER_ID, null, this)
    }

    /**
     * Permissions not granted. Quit.
     */
    private fun onPermissionsNotGranted() {
        Toast.makeText(this, R.string.toast_permissions_not_granted, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        menuInflater.inflate(R.menu.main, menu)
        val searchItem = menu.findItem(R.id.item_search)
        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        searchView.setOnQueryTextListener(this)
        return true
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Collection<MusicItem>> {
        return if (id == MUSIC_LOADER_ID)
            MusicLoader(this)
        else
            throw Exception("Not expected $id")
    }

    override fun onLoadFinished(
        loader: Loader<Collection<MusicItem>>,
        data: Collection<MusicItem>
    ) {
        adapter!!.let {
            it.addAll(data)
            it.notifyItemRangeInserted(0, data.size)
        }
        MusicService.setTracks(this, data.toTypedArray())
    }

    override fun onLoaderReset(loader: Loader<Collection<MusicItem>>) {
        adapter!!.let {
            val size = it.itemCount
            it.clear()
            it.notifyItemRangeRemoved(0, size)
        }
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        adapter?.filter?.filter(newText)
        return true
    }

    override fun onDestroy() {
        emptyViewObserver?.unbind()
        super.onDestroy()
    }

    /**
     * Check if service is running.
     *
     * @param serviceClass
     * @return
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val MUSIC_LOADER_ID = 1
        private const val OVERLAY_PERMISSION_REQ_CODE = 1
        private const val EXT_STORAGE_PERMISSION_REQ_CODE = 2
    }
}