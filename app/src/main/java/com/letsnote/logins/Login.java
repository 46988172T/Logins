package com.letsnote.logins;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import android.support.multidex.MultiDex;
import android.util.Base64;
import android.util.Log;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;

import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Login extends Activity {

    private CallbackManager callbackManager;
    private LoginButton loginButton;
    private TextView btnLogin;
    private ProgressDialog progressDialog;
    User user;
    private int contador = 0; //para firebase

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this);
        MultiDex.install(this);
        FacebookSdk.sdkInitialize(getApplicationContext());
        setContentView(R.layout.activity_login);


         /*
            Esta parte de código nos devuelve el debug keyhash, no es necesario para la aplicación, pero si para el desarrollo
            y poder utilizar el login de facebook.
         */
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    "com.letsnote.logins",  // replace with your unique package name
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {

        } catch (NoSuchAlgorithmException e) {

        }

        // hasta aqui debug keyhash


        if(PrefUtils.getCurrentUser(Login.this) != null){

            Intent homeIntent = new Intent(Login.this, FacebookLogout.class);

            startActivity(homeIntent);

            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


        callbackManager=CallbackManager.Factory.create();

        loginButton= (LoginButton)findViewById(R.id.login_button);

        loginButton.setReadPermissions("public_profile","email","user_friends");

        btnLogin= (TextView) findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                progressDialog = new ProgressDialog(Login.this);
                progressDialog.setMessage("Cargando...");
                progressDialog.show();

                loginButton.performClick();

                loginButton.setPressed(true);

                loginButton.invalidate();

                loginButton.registerCallback(callbackManager, mCallBack);

                loginButton.setPressed(false);

                loginButton.invalidate();

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }


    private FacebookCallback<LoginResult> mCallBack = new FacebookCallback<LoginResult>() {
        @Override
        public void onSuccess(LoginResult loginResult) {

            progressDialog.dismiss();

            // App code
            GraphRequest request = GraphRequest.newMeRequest(
                    loginResult.getAccessToken(),
                    new GraphRequest.GraphJSONObjectCallback() {
                        @Override
                        public void onCompleted(
                                final JSONObject object,
                                GraphResponse response) {

                            Log.e("Respuesta: ", response + "");

                            try {
                                //guardamos el usuario
                                try {
                                    user = new User();
                                    Bundle bFacebookData = getFacebookData(object);
                                    user.setFacebookID(bFacebookData.getString("idFacebook").toString());
                                    user.setEmail(bFacebookData.getString("email").toString());
                                    user.setName(bFacebookData.getString("name").toString());
                                    user.setGender(bFacebookData.getString("gender").toString());
                                    user.setPictureUrl(bFacebookData.getString("profile_pic"));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                /*
                                 * Llamamos a firebase, vamos a buscar la id para comprobar que no existe.
                                 */
                                Firebase ref = new Firebase("https://loginsfbleo.firebaseio.com/users/user/");
                                ref.addListenerForSingleValueEvent(new ValueEventListener() {

                                    @Override
                                    public void onDataChange(DataSnapshot snapshot) {
                                        for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                                            //creamos un nuevo usuario que al que daremos los valores del snapshot iterado. se sobreescribira en cada iteracion
                                            // y compararemos la id del usuario que se esta logueando con las ya guardadas.
                                            User userSnapshot = postSnapshot.getValue(User.class);
                                            System.out.println("-------------------------------------------> " + userSnapshot.getFacebookID());
                                            if (userSnapshot.getFacebookID().equals(user.getFacebookID())) {
                                                //si existe ya el usuario en Firebase, el contador se pone a cero y sale.
                                                contador = 0;
                                                break;
                                            } else if (!userSnapshot.getFacebookID().equals(user.getFacebookID())) {
                                                //si no existe, va sumando hasta que acabe.
                                                contador = contador + 1;
                                            }
                                        }
                                        //cuando acaba el for, le pasamos el contador y el user al metodo de guardado en firebase
                                        addInfoFirebase(contador, user);
                                    }

                                    @Override
                                    public void onCancelled(FirebaseError firebaseError) {
                                    }

                                });

                                    PrefUtils.setCurrentUser(user, Login.this);

                                }catch (Exception e){
                                    e.printStackTrace();
                                }

                                Toast.makeText(Login.this,"Bienvenido "+user.name,Toast.LENGTH_LONG).show();
                                Intent intent=new Intent(Login.this, FacebookLogout.class);
                                startActivity(intent);
                                finish();

                        }

                    });

            Bundle parameters = new Bundle();
            parameters.putString("fields", "id, name, first_name, last_name, email,gender, birthday, location");
            request.setParameters(parameters);
            request.executeAsync();
        }

        @Override
        public void onCancel() {
            progressDialog.dismiss();
        }

        @Override
        public void onError(FacebookException e) {
            progressDialog.dismiss();
        }
    };


    /**
     * Nos crea un objecto tipo Bundle, que guardará los datos del parámetro que le pasamos, que es un JSONObject
     * con las 'key' que le asignemos.
     * La recuperación se haría de la siguiente manera:
     *
     * Bundle nombreDelBundle = getFacebookData(object);
     * nombreDelBundle.getString("idFacebook").toString(); -> tiene key 'idFacebook'.
     *
     * @param object
     * @return
     * @throws JSONException
     */
    private Bundle getFacebookData(JSONObject object) throws JSONException {

            Bundle bundle = new Bundle();
            String id = object.getString("id");

            try {
                URL profile_pic = new URL("https://graph.facebook.com/" + id + "/picture?width=200&height=150");
                Log.i("profile_pic", profile_pic + "");
                bundle.putString("profile_pic", profile_pic.toString());

            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            }

            bundle.putString("idFacebook", id);
            if (object.has("name"))
            bundle.putString("name", object.getString("name"));
            if (object.has("first_name"))
                bundle.putString("first_name", object.getString("first_name"));
            if (object.has("last_name"))
                bundle.putString("last_name", object.getString("last_name"));
            if (object.has("email"))
                bundle.putString("email", object.getString("email"));
            if (object.has("gender"))
                bundle.putString("gender", object.getString("gender"));
            if (object.has("birthday"))
                bundle.putString("birthday", object.getString("birthday"));
            if (object.has("location"))
                bundle.putString("location", object.getJSONObject("location").getString("name"));

            return bundle;
    }

    /**
     * Método para convertir a bitmap la imagen referenciada por a URL.
     * @param url
     * @return
     * @throws IOException
     */
    public static Bitmap getFacebookProfilePicture(String url) throws IOException {
        URL facebookProfileURL= new URL(url);
        Bitmap bitmap = BitmapFactory.decodeStream(facebookProfileURL.openConnection().getInputStream());
        return bitmap;
    }

    /**
     * Este es el método para guardar los datos del usuario que se loguea en Firebase.
     * Se le pasa el parametro contador, que hemos declarado al principio de la clase y que vale cero de inicio,
     * pero dependiendo de comprobaciones anteriores puede seguir siendo cero, y no se gestionará ninguna acción de guardado,
     * o diferente a cero, en este último caso sí guardaremos los datos del usuario 'user', que es el otro
     * parámetro que se le pasa.
     * @param contador
     * @param user
     */
    public static void addInfoFirebase(int contador, User user){

        if(contador != 0) {
            /*
             * si el contador no esta a cero es que no lo ha encontrado en ningun momento y el numero será igual a los usuarios que haya. GUARDAMOS LOS DATOS del usuario.
             */
            final Firebase refUsers = new Firebase("https://loginsfbleo.firebaseio.com/").child("users").child("user").push();
            refUsers.setValue(user);
        }else{
            /*
             * si el contador esta a cero es que ha encontrado los datos y aunque en iteraciones anteriores no lo hubiera hecho y el contador hubiera sumado, anteriormente le indicamos
             * lo ponga a cero antes de salir del bucle, y no guardamos nada.
             */
        }
    }

}