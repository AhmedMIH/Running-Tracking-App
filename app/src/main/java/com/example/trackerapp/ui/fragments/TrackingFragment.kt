package com.example.trackerapp.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.trackerapp.R
import com.example.trackerapp.db.Run
import com.example.trackerapp.other.CONST.ACTION_PAUSE_SERVICE
import com.example.trackerapp.other.CONST.ACTION_START_OR_RESUME_SERVICE
import com.example.trackerapp.other.CONST.ACTION_STOP_SERVICE
import com.example.trackerapp.other.CONST.CANCEL_TRACKING_DIALOG_TAG
import com.example.trackerapp.other.CONST.MAP_ZOOM
import com.example.trackerapp.other.CONST.POLYLINE_COLOR
import com.example.trackerapp.other.CONST.POLYLINE_WIDTH
import com.example.trackerapp.other.TrackingUtility
import com.example.trackerapp.services.Polyline
import com.example.trackerapp.services.TrackingService
import com.example.trackerapp.ui.viewModels.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_tracking.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import kotlin.math.round

@AndroidEntryPoint
class TrackingFragment : Fragment(R.layout.fragment_tracking) {
    private val viewModel: MainViewModel by viewModels()
    private var map: GoogleMap? = null
    private var isTracking = false
    private var pathPoints = mutableListOf<Polyline>()
    private var currentTimeMillis = 0L
    private var menu: Menu? = null

    @set:Inject
    var weight = 80f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView.onCreate(savedInstanceState)
        tvTimer.text = "00:00:00:00"
        btnToggleRun.setOnClickListener {
            toggleRun()
        }
        if (savedInstanceState != null) {
            val cancelTrackingDialog = parentFragmentManager.findFragmentByTag(
                CANCEL_TRACKING_DIALOG_TAG ) as CancelTrackingDialog
            cancelTrackingDialog?.setYesListener {
                stopRun()
            }
        }
        mapView.getMapAsync {
            map = it
            addAllPolyline()
        }
        btnFinishRun.setOnClickListener {
            zoomToSeeWholeTrack()
            saveRunOnDb()
        }
        subscribeToObservers()
        Timber.d(currentTimeMillis.toString())
        Timber.d(isTracking.toString())
        Timber.d(pathPoints.toString())

    }

    private fun subscribeToObservers() {
        TrackingService.isTracking.observe(viewLifecycleOwner, Observer {
            updateTracking(it)
        })
        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyline()
            movieCameraToUser()
        })
        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            currentTimeMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(currentTimeMillis, true)
            tvTimer.text = formattedTime
        })
    }

    private fun toggleRun() {
        if (isTracking) {
            menu?.getItem(0)?.isVisible = true
            sendCommendToService(ACTION_PAUSE_SERVICE)
        } else {
            sendCommendToService(ACTION_START_OR_RESUME_SERVICE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolber_tracking_menu, menu)
        this.menu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (currentTimeMillis > 0L) {
            this.menu?.getItem(0)?.isVisible = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.miCancelTracking -> {
                showCancelTrackingDialog()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showCancelTrackingDialog() {
        CancelTrackingDialog().apply {
            setYesListener {
                stopRun()
            }
        }.show(parentFragmentManager, CANCEL_TRACKING_DIALOG_TAG)
    }

    private fun stopRun() {
        sendCommendToService(ACTION_STOP_SERVICE)
        val navOption = NavOptions.Builder()
            .setPopUpTo(R.id.trackingFragment, true)
            .build()
        findNavController().navigate(R.id.action_trackingFragment_to_runFragment,null,navOption)
    }

    private fun updateTracking(isTracking: Boolean) {
        this.isTracking = isTracking
        if (!isTracking && currentTimeMillis > 0L) {
            btnToggleRun.text = "Start"
            btnFinishRun.visibility = View.VISIBLE
        } else if (isTracking) {
            btnToggleRun.text = "Stop"
            menu?.getItem(0)?.isVisible = true
            btnFinishRun.visibility = View.GONE
        }
    }

    private fun zoomToSeeWholeTrack() {

        val bounds = LatLngBounds.builder()
        Timber.d(bounds.toString())
        if (pathPoints.isNotEmpty()) {
            for (polyline in pathPoints) {
                for (pos in polyline) {
                    bounds.include(pos)
                }
            }
            map?.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds.build(),
                    mapView.width,
                    mapView.height,
                    (mapView.height * 0.05f).toInt()
                )
            )
        } else {
            btnFinishRun.visibility = View.GONE
            Snackbar.make(
                requireActivity().findViewById(R.id.rootView),
                "pleas start running ",
                Snackbar.LENGTH_LONG
            ).show()
        }

    }

    private fun saveRunOnDb() {
        if (pathPoints.isNotEmpty()) {
            map?.snapshot { bmp ->
                var distanceInMeters = 0
                for (polyline in pathPoints) {
                    distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
                }
                val avgSpeed =
                    round((distanceInMeters / 1000f) / (currentTimeMillis / 1000f / 60f / 60f) * 10) / 10f
                val dataTimestamp = Calendar.getInstance().timeInMillis
                val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()
                val run = Run(
                    bmp,
                    dataTimestamp,
                    avgSpeed,
                    distanceInMeters,
                    currentTimeMillis,
                    caloriesBurned
                )
                viewModel.insertRun(run)
                Snackbar.make(
                    requireActivity().findViewById(R.id.rootView),
                    "Run Saved Successfully",
                    Snackbar.LENGTH_LONG
                ).show()
                stopRun()
            }
        }

    }

    private fun addAllPolyline() {
        for (polyline in pathPoints) {
            val polygonOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyline)
            map?.addPolyline(polygonOptions)
        }
    }

    private fun addLatestPolyline() {
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1) {
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polygonOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)
            map?.addPolyline(polygonOptions)
        }
    }

    private fun movieCameraToUser() {
        if (pathPoints.isNotEmpty() && pathPoints.last().isNotEmpty()) {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pathPoints.last().last(),
                    MAP_ZOOM
                )
            )
        }
    }

    private fun sendCommendToService(action: String) {
        Intent(requireContext(), TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

}