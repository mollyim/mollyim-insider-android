package org.thoughtcrime.securesms.registration.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

public final class LinkDeviceNameFragment extends LoggingFragment {

  private static final String TAG = Log.tag(LinkDeviceNameFragment.class);

  private LabeledEditText       deviceName;
  private View                  linkDevice;
  private RegistrationViewModel viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_link_device_name, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    deviceName = view.findViewById(R.id.device_name);
    linkDevice = view.findViewById(R.id.link_button);

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

    deviceName.getInput().addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {
        if (s == null) return;

        viewModel.setDeviceName(s.toString());
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
    });
    linkDevice.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), LinkDeviceNameFragmentDirections.actionLinkDevice()));
  }
}
