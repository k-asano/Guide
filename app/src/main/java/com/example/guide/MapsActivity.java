package com.example.guide;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.places.GeoDataApi;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MapsActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, OnMapReadyCallback, SensorEventListener{

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient mGoogleApiClient;
    private GoogleApiClient mGooglePlaceApiClient;
    private FusedLocationProviderApi fusedLocationProviderApi = LocationServices.FusedLocationApi;
    private LocationRequest locationRequest;
    private Location location;
    private GeoDataApi mGeoDataApi;
    private AutoCompleteTextView mTextView;
    private PlaceAutoCompleteAdapter mAdapter;
    private LatLng target;
    private LatLng nowTarget;
    private int mode;
    private final static int MAP = 0;
    private final static int GUIDE = 1;
    private LinearLayout searchComponents;
    private LinearLayout.LayoutParams searchComponentsParams;
    private ScheduledExecutorService ses = null;
    private SensorManager mSensorManager;
    private final int MATRIX_SIZE = 16;
    private final int DIMENSION = 3;
    private boolean moved;

    private AdapterView.OnItemClickListener mAutocompleteClickListener
            = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            /*
             Retrieve the place ID of the selected item from the Adapter.
             The adapter stores each Place suggestion in a PlaceAutocomplete object from which we
             read the place ID.
              */
            final PlaceAutoCompleteAdapter.PlaceAutocomplete item = mAdapter.getItem(position);
            final String placeId = String.valueOf(item.placeId);
            Log.i("AutoCompleteText", "Autocomplete item selected: " + item.description);

            /*
             Issue a request to the Places Geo Data API to retrieve a Place object with additional
              details about the place.
              */
            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi
                    .getPlaceById(mGooglePlaceApiClient, placeId);

            Log.i("AutoCompleteText", "Called getPlaceById to get Place details for " + item.placeId);

            EditText editText1 = (EditText) findViewById(R.id.autoCompleteText);
            Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
            try {
                List<Address> addressList = geocoder.getFromLocationName(editText1.getText().toString(), 1);
                Address address = addressList.get(0);
                double lat = address.getLatitude();
                double lng = address.getLongitude();
                mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).title(editText1.getText().toString()));
                mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                closeSoftwareInput();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private float[] magneticValues;
    private float[] accelerometerValues;

    private void closeSoftwareInput(){
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mTextView.getWindowToken(), 0);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = MAP;
        setContentView(R.layout.activity_maps);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        searchComponents = (LinearLayout) findViewById(R.id.search_components);
        searchComponentsParams = (LinearLayout.LayoutParams) searchComponents.getLayoutParams();
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(16);

        mGooglePlaceApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this,0,this)
                .addApi(Places.GEO_DATA_API)
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mTextView = (AutoCompleteTextView) findViewById(R.id.autoCompleteText);
        mTextView.setOnItemClickListener(mAutocompleteClickListener);
        mTextView.setOnKeyListener( new View.OnKeyListener() {

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {

                //EnterKeyが押されたかを判定
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_ENTER) {

                    //ソフトキーボードを閉じる
                    closeSoftwareInput();

                    return true;
                }
                return false;
            }
        });

        Button search = (Button) findViewById(R.id.search);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String str = mTextView.getText().toString();
                Geocoder geocoder = new Geocoder(v.getContext(),Locale.getDefault());

                try{
                    List<Address> addressList = geocoder.getFromLocationName(str, 1);
                    if(addressList.size() > 0) {
                        Address address = addressList.get(0);
                        double lat = address.getLatitude();
                        double lng = address.getLongitude();
                        mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).title(mTextView.getText().toString()));
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
                        closeSoftwareInput();
                    }
                }catch(IOException e){
                }
            }
        });
        mAdapter = new PlaceAutoCompleteAdapter(this, android.R.layout.simple_list_item_1,
                mGooglePlaceApiClient, null, null);
        mTextView.setAdapter(mAdapter);
        setUpMapIfNeeded();
    }

    private GoogleMap.OnMapLongClickListener mMapLongClickListener = new GoogleMap.OnMapLongClickListener() {
        @Override
        public void onMapLongClick(LatLng latLng) {
           target = latLng;
           makeConfirmDialog().show();
        }
    };

    private GoogleMap.OnMarkerClickListener mMarkerClickListener = new GoogleMap.OnMarkerClickListener() {
        @Override
        public boolean onMarkerClick(Marker marker) {
            target = marker.getPosition();
            makeConfirmDialog().show();
            return false;
        }
    };

    private AlertDialog.Builder makeConfirmDialog(){
        AlertDialog.Builder dialog = new AlertDialog.Builder(MapsActivity.this);
        dialog.setMessage("ここを目的地に設定しますか？");

        dialog.setPositiveButton("はい", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                nowTarget = target;
                mMap.addMarker(new MarkerOptions().position(nowTarget).title(""));
                changeToGuideMode();
            }
        });

        dialog.setNegativeButton("いいえ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        return dialog;
    }

    private void changeToMapMode(){
        if(mode == GUIDE) {
            Log.d("aaaaaaaaaaaa","ses.shutdown");
            ses.shutdown();
            ses = null;
            mode = MAP;
            LinearLayout llOutside = (LinearLayout) findViewById(R.id.map_outside);
            View map = findViewById(R.id.map);
            LinearLayout.LayoutParams lpMap = (LinearLayout.LayoutParams) map.getLayoutParams();
            lpMap.weight = 0.0f;

            llOutside.removeAllViews();
            llOutside.addView(searchComponents, searchComponentsParams);
            llOutside.addView(map, lpMap);
        }
    }
    private void changeToGuideMode(){
        if(mode == MAP) {
            mode = GUIDE;
            LinearLayout llOutside = (LinearLayout) findViewById(R.id.map_outside);
            //LinearLayout llSearch = (LinearLayout) findViewById(R.id.search_components);
            double lat = mMap.getMyLocation().getLatitude();
            double lng = mMap.getMyLocation().getLongitude();
            GuideView gvGuide = new GuideView(this,nowTarget,new LatLng(lat,lng));
            LinearLayout.LayoutParams lpGuide = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);
            lpGuide.weight = 0.5f;

            Button bFinish = new Button(this);
            bFinish.setText("案内を終了する");
            bFinish.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeToMapMode();
                }
            });
            LinearLayout.LayoutParams lpFinish = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            View map = findViewById(R.id.map);
            LinearLayout.LayoutParams lpMap = (LinearLayout.LayoutParams) map.getLayoutParams();
            lpMap.weight = 0.5f;

            llOutside.removeAllViews();
            llOutside.addView(gvGuide, lpGuide);
            llOutside.addView(bFinish, lpFinish);
            llOutside.addView(map, lpMap);

            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 18.0f));
            startAnimate();
        }
    }

    private GoogleMap.OnCameraChangeListener mCameraChangeListener = new GoogleMap.OnCameraChangeListener() {
        @Override
        public void onCameraChange(CameraPosition cameraPosition) {
            moved = true;
        }
    };

    private GoogleMap.OnMyLocationButtonClickListener mMyLocationButtonClickListener = new GoogleMap.OnMyLocationButtonClickListener() {
        @Override
        public boolean onMyLocationButtonClick() {
            switch(mode) {
                case MAP:
                    double lat = mMap.getMyLocation().getLatitude();
                    double lng = mMap.getMyLocation().getLongitude();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lng),18.0f));
                    moved = false;
                    break;
                case GUIDE:
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(nowTarget, 18.0f));
                    break;
            }
            return true;
        }
    };

    private void startAnimate(){
        Log.d("aaaaaaaaaaaaaaaa","startANimate");
        LinearLayout llOutside = (LinearLayout) findViewById(R.id.map_outside);
        GuideView gv = (GuideView) llOutside.getChildAt(0);
        ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(gv.getTask(),0L,100L,TimeUnit.MILLISECONDS);
    }
    @Override
    protected void onStart() {
        if(!mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        moved = false;
        setUpMapIfNeeded();
        mSensorManager.registerListener(this,mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this,mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_UI);
        if(mode == GUIDE){
            startAnimate();
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
        fusedLocationProviderApi.removeLocationUpdates(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
        if(ses != null) {
            Log.d("aaaaaaaaaaaa","ses.shutdown");
            ses.shutdown();
            ses = null;
        }
    }

    @Override
    protected void onPause(){
        mSensorManager.unregisterListener(this);
        super.onPause();
    }
    @Override
    protected  void onDestroy(){
        super.onDestroy();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            SupportMapFragment smf = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            smf.getMapAsync(this);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location currentLocation = fusedLocationProviderApi.getLastLocation(mGoogleApiClient);
        if (currentLocation != null) {
            location = currentLocation;
            Log.d("onConnected",location.toString());
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            LatLng cur = new LatLng(lat,lng);
            mAdapter.setBounds(new LatLngBounds(new LatLng(lat-0.05f,lng-0.05f),new LatLng(lat+0.05f, lng+0.05f)));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(cur,17.0f));

        }
        fusedLocationProviderApi.requestLocationUpdates(mGoogleApiClient, locationRequest, MapsActivity.this);

    }

    @Override
    public void onLocationChanged(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        if(moved == false && mode == MAP) {
            mAdapter.setBounds(new LatLngBounds(new LatLng(lat - 0.05f, lng - 0.05f), new LatLng(lat + 0.05f, lng + 0.05f)));
            mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
        }
        if(mode == GUIDE){
            GuideView gv = (GuideView) ((LinearLayout) findViewById(R.id.map_outside)).getChildAt(0);
            gv.setMyPosition(new LatLng(lat,lng));

        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMyLocationButtonClickListener(mMyLocationButtonClickListener);
        mMap.setOnMapLongClickListener(mMapLongClickListener);
        mMap.setOnMarkerClickListener(mMarkerClickListener);
        mMap.setOnCameraChangeListener(mCameraChangeListener);
        googleMap.setMyLocationEnabled(true);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return;
        }

        if(mode == GUIDE) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_MAGNETIC_FIELD: // 地磁気センサ
                    magneticValues = event.values.clone();
                    break;
                case Sensor.TYPE_ACCELEROMETER:  // 加速度センサ
                    accelerometerValues = event.values.clone();
                    break;
            }

            if (magneticValues != null && accelerometerValues != null) {
                float[] rotationMatrix = new float[MATRIX_SIZE];
                float[] inclinationMatrix = new float[MATRIX_SIZE];
                float[] remapedMatrix = new float[MATRIX_SIZE];

                float[] orientationValues = new float[DIMENSION];

                SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues);
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapedMatrix);
                SensorManager.getOrientation(remapedMatrix, orientationValues);

                GuideView gv = (GuideView) ((LinearLayout) findViewById(R.id.map_outside)).getChildAt(0);
                gv.setMyDirection(-orientationValues[0]);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
