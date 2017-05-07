package com.android.eu.proximitymap.activities;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.eu.proximitymap.R;
import com.android.eu.proximitymap.Utils.MarkerManager;
import com.android.eu.proximitymap.Utils.PermissionHelper;
import com.android.eu.proximitymap.models.SimpleLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ittianyu.bottomnavigationviewex.BottomNavigationViewEx;

/**
 * Map activity, default activity after logging in.
 * <p>
 * Navigation bar documentation: https://github.com/ittianyu/BottomNavigationViewEx
 */
public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ChildEventListener,
        BottomNavigationView.OnNavigationItemSelectedListener {

    private static final int NAV_HEIGHT = 190;
    private static final int INITIAL_ZOOM = 16;
    private static final float NAV_ICON_SIZE = 32f;
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private FirebaseUser mUser;
    private boolean firstTimeZoom = false;
    private MarkerManager markerManager;
    private DatabaseReference mDatabase;
    private PermissionHelper permissionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Navigation settings.
        BottomNavigationViewEx bnve = (BottomNavigationViewEx) findViewById(R.id.bottom_navigation);
        bnve.enableAnimation(true);
        bnve.enableItemShiftingMode(true);
        bnve.setTextVisibility(false);
        bnve.setIconSize(NAV_ICON_SIZE, NAV_ICON_SIZE);
        bnve.setItemHeight(NAV_HEIGHT);
        bnve.setOnNavigationItemSelectedListener(this);

        // Request location permission.
        permissionHelper = new PermissionHelper(this);
        permissionHelper.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, MY_PERMISSIONS_REQUEST_LOCATION);

        // Assign the current user.
        mUser = FirebaseAuth.getInstance().getCurrentUser();
        if (mUser == null) {
            Log.wtf("ToPkEk", "CaN't HaPpEn! ImPoSsIbLe StAte!");
            throw new NullPointerException("User not logged in???");
        }

        // Firebase database position reference.
        mDatabase = FirebaseDatabase.getInstance().getReference("locations");

        // Get existing markers.
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        markerManager.addMarker(snapshot);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });

        // Assign a listener for position changes.
        mDatabase.addChildEventListener(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // TODO: Clickable markers.
        // http://stackoverflow.com/questions/14226453/google-maps-api-v2-how-to-make-markers-clickable
        // mMap.setOnMarkerClickListener(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the mUser will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the mUser has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        } else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        markerManager = new MarkerManager(mMap, mUser.getUid());
    }

    /**
     * Handles the actions for the bottom navigation bar.
     *
     * @param item that is clicked.
     * @return whether the action is handled.
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_map:
                Log.v("NAV", "MAP");
                return true;

            case R.id.action_user:
                Log.v("NAV", "USER");
                return true;

            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
        }
        return false;
    }

    /**
     * Called when the location has changed.
     *
     * @param location The new location, as a Location object.
     */
    @Override
    public void onLocationChanged(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        //move map camera
        if (!firstTimeZoom) {
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(INITIAL_ZOOM));
            firstTimeZoom = true;
        }

        // Update location in the database.
        updateLocation(mUser.getUid(), latLng, mUser.getDisplayName());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        } else {
            Log.e("PERMISSIONS", "USER DENIED LOCATION PERMISSION");
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            finish();
                            break;
                    }
                }
            };
            // No picture selected, sure you want to continue?
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("You can't use this application without GPS permissions")
                    .setPositiveButton("I understand", dialogClickListener).show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) { // Do nothing
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Couldn't connect to the servers.", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (permissionHelper.hasLocationPermission()) {
                    if (mGoogleApiClient == null) {
                        buildGoogleApiClient();
                    }
                    mMap.setMyLocationEnabled(true);
                } else {
                    // Permission denied, Disable the functionality that depends on this permission.
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show();
                }
            }
        }

    }

    /**
     * Dispatch onStop() to all fragments.  Ensure all loaders are stopped.
     */
    @Override
    protected void onStop() {
        super.onStop();
        //stop location updates
        if (mGoogleApiClient != null) {
            Log.e("onStop", "Removing location updates.");
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }

        // TODO: Decide on this functionality.
        // Remove your own location entry.
        // mDatabase.getRoot().child("locations").child(mUser.getUid()).removeValue();
    }

    /**
     * This method is triggered when the data at a child location has changed.
     *
     * @param dataSnapshot An immutable snapshot of the data at the new data at the child location
     * @param s            The key name of sibling location ordered before the child.
     *                     This will be null for the first child node of a location.
     */
    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
        markerManager.addMarker(dataSnapshot);
    }

    /**
     * This method is triggered when the data at a child location has changed. Update the marker
     * location by calling addMarker().
     *
     * @param dataSnapshot An immutable snapshot of the data at the new data at the child location
     * @param s            The key name of sibling location ordered before the child.
     *                     This will be null for the first child node of a location.
     */
    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
        markerManager.addMarker(dataSnapshot);
    }

    /**
     * This method is triggered when a child is removed from the
     * location to which this listener was added. Remove the corresponding marker on the map.
     *
     * @param dataSnapshot An immutable snapshot of the data at the child that was removed.
     */
    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        markerManager.remove(dataSnapshot.getKey());
    }

    /**
     * This method is triggered when a child location's priority changes.
     *
     * @param dataSnapshot An immutable snapshot of the data at the location that moved.
     * @param s            The key name of the sibling location ordered before the child location.
     *                     This will be null if this location is ordered first.
     */
    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
        Log.v("DATABASE", "onChildMoved");
    }

    /**
     * This method will be triggered in the event that this listener either failed at the server,
     * or is removed as a result of the security and Firebase rules.
     *
     * @param databaseError A description of the error that occurred
     */
    @Override
    public void onCancelled(DatabaseError databaseError) {
        Log.d("DATABASE", databaseError.getMessage() + databaseError.getDetails());
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    /**
     * Updates the location of the current mUser in the database.
     *
     * @param userId the userId of the current mUser.
     * @param latLng current coordinates.
     */
    private void updateLocation(String userId, LatLng latLng, String name) {
        SimpleLocation loc = new SimpleLocation(latLng.latitude, latLng.longitude, name);
        mDatabase.child(userId).setValue(loc);
    }

    /**
     * Vibrates the phone for 400 ms.
     */
    private void vibrate() {
        // Get instance of Vibrator from current Context
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (v.hasVibrator()) {
            v.vibrate(400);
        }
    }
}
