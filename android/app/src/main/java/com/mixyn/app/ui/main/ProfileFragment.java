package com.mixyn.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.welcome.WelcomeActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Profile tab: who you are, and Log out. Everything else here is "coming soon". */
public class ProfileFragment extends Fragment {

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        SessionStore session = new SessionStore(requireContext());
        ((TextView) view.findViewById(R.id.profile_name)).setText(session.getName());
        ((TextView) view.findViewById(R.id.profile_details))
                .setText(getString(R.string.profile_email_city, session.getEmail(), session.getCity()));
        view.findViewById(R.id.profile_city).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.coming_soon, Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.profile_logout).setOnClickListener(v -> logOut(session));
    }

    private void logOut(SessionStore session) {
        // Tell the server to forget the token, but log out locally right away either way.
        ApiClient.get(requireContext()).logout("Bearer " + session.getToken()).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
            }
        });
        session.clear();
        startActivity(new Intent(requireContext(), WelcomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
