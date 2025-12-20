package eu.piotro.sondechaser.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;


import java.util.Objects;

import eu.piotro.sondechaser.BuildConfig;
import eu.piotro.sondechaser.MainActivity;
import eu.piotro.sondechaser.R;

public class HomeFragment extends Fragment {
    public MapView mapView;
    public MyLocationNewOverlay locationOverlay;

    private MapUpdater updater;

    private View view = null;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        v.findViewById(R.id.tvwait).setVisibility(View.VISIBLE);
//        Thread init_thread = new Thread(this::initView);
//        init_thread.start();
        initView();
    }

    private void initView() {
        View v = view;
        mapView = v.findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        // from OSMDroid guidelines
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(v.getContext()), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        RotationGestureOverlay rotationOverlay = new RotationGestureOverlay(mapView);
        rotationOverlay.setEnabled(true);
        mapView.setMultiTouchControls(true);
        mapView.getOverlays().add(rotationOverlay);

        mapView.post(
                () -> {
                    mapView.getController().setZoom(11.0);
                    boolean set = false;
                    try {
                        if (((MainActivity) requireActivity()).dataCollector.locationProvider.getLastKnownLocation() != null) {
                            mapView.getController().setCenter(new GeoPoint(((MainActivity) requireActivity()).dataCollector.locationProvider.getLastKnownLocation()));
                            set = true;
                        }
                    } catch (Exception ignored) {}

                    if(!set)
                        mapView.getController().setCenter(new GeoPoint(51.107, 17.038));
                }
        );

        updater = new MapUpdater(this, v, getActivity());

        v.findViewById(R.id.northbtn).setOnClickListener((_v) -> mapView.setMapOrientation(0));
        v.findViewById(R.id.locbtn).setOnClickListener((_v) -> {
            if (locationOverlay.getMyLocation() == null) {
                Toast.makeText(getContext(), "No location", Toast.LENGTH_SHORT).show();
                return;
            }
            mapView.getController().animateTo(locationOverlay.getMyLocation());
        });

        v.findViewById(R.id.posbtn).setOnClickListener((_v) -> {
            mapView.getController().animateTo(updater.last_pos);
        });
        v.findViewById(R.id.predbtn).setOnClickListener((_v) -> {
            mapView.getController().animateTo(updater.last_pred);
        });

        final TextView mapSourceAttributionTextView = v.findViewById(R.id.mapSourceAttributionTextView);
        v.findViewById(R.id.mapsourcebtn).setOnClickListener((_v) -> {
            PopupMenu popup = new PopupMenu(HomeFragment.this.getContext(), _v);
            popup.getMenu().add("Mapnik (Default)");
            popup.getMenu().add("Carto Voyager");
            popup.getMenu().add("OpenTopoMap");

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();

                switch (title) {
                    case "Mapnik (Default)":
                        mapView.setTileSource(TileSourceFactory.MAPNIK);
                        mapSourceAttributionTextView.setText("Map ©OpenStreetMap contributors");
                        break;

                    case "Carto Voyager":
                        mapView.setTileSource(new OnlineTileSourceBase(
                                "CartoVoyager", 0, 19, 256, "",
                                new String[]{"https://a.basemaps.cartocdn.com/rastertiles/voyager/"}) {
                            @Override
                            public String getTileURLString(long pMapTileIndex) {
                                return getBaseUrl()
                                        + MapTileIndex.getZoom(pMapTileIndex) + "/"
                                        + MapTileIndex.getX(pMapTileIndex) + "/"
                                        + MapTileIndex.getY(pMapTileIndex) + ".png";
                            }
                        });
                        mapSourceAttributionTextView.setText("Map ©CartoVoyager contributors");
                        break;

                    case "OpenTopoMap":
                        mapView.setTileSource(new OnlineTileSourceBase(
                                "OpenTopoMap", 0, 17, 256, "",
                                new String[]{"https://a.tile.opentopomap.org/"}) {
                            @Override
                            public String getTileURLString(long pMapTileIndex) {
                                return getBaseUrl()
                                        + MapTileIndex.getZoom(pMapTileIndex) + "/"
                                        + MapTileIndex.getX(pMapTileIndex) + "/"
                                        + MapTileIndex.getY(pMapTileIndex) + ".png";
                            }
                        });
                        mapSourceAttributionTextView.setText("Map ©OpenTopoMap contributors");
                        break;
                }

                mapView.invalidate();
                Toast.makeText(HomeFragment.this.getContext(), "Switched to " + title, Toast.LENGTH_SHORT).show();
                return true;
            });

            popup.show();
        });

        while (getActivity() == null) {}
        v.findViewById(R.id.refrbtn).setOnClickListener((_v) -> {
            try {
                ((MainActivity) getActivity()).dataCollector.refresh();
            } catch (Exception ignored) {
            }
        });

        SharedPreferences sharedPref = requireActivity().getSharedPreferences("eu.piotro.sondechaser.PSET", Context.MODE_PRIVATE);
        boolean show_welcome = sharedPref.getBoolean("first_run", true);
        if(show_welcome) {
            requireActivity().runOnUiThread(()-> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Welcome!")
                        .setMessage("Hi! Thanks for downloading my app.\nYou can find the app GUIDE in the side panel (expanded by ☰)\nHappy Hunting!")
                        .setPositiveButton("Don't show again",
                                (dialog, which) -> sharedPref.edit().putBoolean("first_run", false).apply()
                        )
                        .show();
            });
        }

        onResume();
    }

    @Override
    public void onPause() {
        try { ((MainActivity) this.requireActivity()).dataCollector.setMapUpdater(null); } catch (Exception e) {e.printStackTrace();}
        if (mapView != null)
            mapView.onPause();
        if (view != null)
            view.findViewById(R.id.tvwait).setVisibility(View.VISIBLE);
        super.onPause();
    }

    @Override
    public void onResume() {
        if (view != null)
            view.findViewById(R.id.tvwait).setVisibility(View.VISIBLE);
        if (mapView != null)
            mapView.onResume();
        try { ((MainActivity)this.requireActivity()).dataCollector.setMapUpdater(updater); } catch (Exception e) {e.printStackTrace();}
        super.onResume();
    }
}