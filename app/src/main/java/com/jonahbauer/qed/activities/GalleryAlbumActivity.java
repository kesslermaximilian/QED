package com.jonahbauer.qed.activities;

import static com.jonahbauer.qed.activities.DeepLinkingActivity.QEDIntent;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.jonahbauer.qed.R;
import com.jonahbauer.qed.activities.imageActivity.ImageActivity;
import com.jonahbauer.qed.activities.sheets.album.AlbumInfoBottomSheet;
import com.jonahbauer.qed.databinding.ActivityGalleryAlbumBinding;
import com.jonahbauer.qed.model.Album;
import com.jonahbauer.qed.model.Image;
import com.jonahbauer.qed.model.Person;
import com.jonahbauer.qed.model.adapter.ImageAdapter;
import com.jonahbauer.qed.model.viewmodel.AlbumViewModel;
import com.jonahbauer.qed.networking.Reason;
import com.jonahbauer.qed.networking.pages.QEDGalleryPages.Filter;
import com.jonahbauer.qed.util.Preferences;
import com.jonahbauer.qed.util.StatusWrapper;
import com.jonahbauer.qed.util.ViewUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GalleryAlbumActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, AdapterView.OnItemClickListener {
    private static final String LOG_TAG = GalleryAlbumActivity.class.getName();
    public static final String GALLERY_ALBUM_KEY = "galleryAlbum";

    private Album mAlbum;
    private AlbumViewModel mAlbumViewModel;
    private ActivityGalleryAlbumBinding mBinding;

    private ImageAdapter mImageAdapter;
    private ArrayAdapter<String> mAdapterCategory;
    private ArrayAdapter<String> mAdapterDate;
    private ArrayAdapter<String> mAdapterPhotographer;

    private ActivityResultLauncher<Intent> mImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        onNewIntent(intent);
        if (isFinishing()) return;

        mAlbumViewModel = new ViewModelProvider(this).get(AlbumViewModel.class);
        mAlbumViewModel.init(mAlbum);

        mBinding = ActivityGalleryAlbumBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        // setup toolbar
        ActionBar actionbar = getSupportActionBar();
        assert actionbar != null;
        actionbar.setDisplayHomeAsUpEnabled(true);

        mImageAdapter = new ImageAdapter(this, new ArrayList<>());
        mBinding.imageContainer.setAdapter(mImageAdapter);
        mBinding.imageContainer.setOnItemClickListener(this);

        mBinding.expandCheckBox.setOnCheckedChangeListener(this);
        mBinding.albumPhotographerCheckBox.setOnCheckedChangeListener(this);
        mBinding.albumDateCheckBox.setOnCheckedChangeListener(this);
        mBinding.albumCategoryCheckBox.setOnCheckedChangeListener(this);

        mAdapterCategory = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        mAdapterCategory.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        mBinding.albumCategorySpinner.setAdapter(mAdapterCategory);
        mBinding.albumCategorySpinner.setEnabled(false);

        mAdapterPhotographer = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        mAdapterPhotographer.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        mBinding.albumPhotographerSpinner.setAdapter(mAdapterPhotographer);
        mBinding.albumPhotographerSpinner.setEnabled(false);

        mAdapterDate = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        mAdapterDate.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        mBinding.albumDateSpinner.setAdapter(mAdapterDate);
        mBinding.albumDateSpinner.setEnabled(false);

        mBinding.searchButton.setOnClickListener(view -> search());

        mBinding.setOnOfflineClick(v -> {
            if (Preferences.gallery().isOfflineMode()) {
                Preferences.gallery().edit().setOfflineMode(false).apply();
            }
            mAlbumViewModel.load();
        });

        adjustColumnCount(getResources().getConfiguration());

        mAlbumViewModel.getFilteredAlbum().observe(this, this::updateView);
        mAlbumViewModel.getOffline().observe(this, offline -> {
            mBinding.setOffline(offline);
            mBinding.setForcedOfflineMode(Preferences.gallery().isOfflineMode());
        });

        mImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null) {
                        Image image = data.getParcelableExtra(ImageActivity.GALLERY_IMAGE_KEY);
                        int index = mImageAdapter.getImages().indexOf(image);
                        if (index != -1) {
                            mBinding.imageContainer.smoothScrollToPosition(index);
                        }
                    }
                }
        );

        mAlbumViewModel.load();
    }

    @NonNull
    private Album getAlbum() {
        StatusWrapper<Album> wrapper = mAlbumViewModel.getAlbum().getValue();
        assert wrapper != null : "StatusWrapper should not be null";
        Album album = wrapper.getValue();
        assert album != null : "Album should not be null";
        return album;
    }

    private void search() {
        HashMap<Filter, String> filterData = new HashMap<>();

        if (mBinding.albumCategoryCheckBox.isChecked()) {
            String category = (String) mBinding.albumCategorySpinner.getSelectedItem();
            if (Album.CATEGORY_ETC.equals(category)) category = "";
            try {
                category = URLEncoder.encode(category, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {}
            filterData.put(Filter.BY_CATEGORY, category);
        }
        if (mBinding.albumDateCheckBox.isChecked()) {
            String[] parts = ((String) mBinding.albumDateSpinner.getSelectedItem()).split("\\.");
            try {
                filterData.put(Filter.BY_DATE, parts[2] + "-" + parts[1] + "-" + parts[0]);
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }
        }
        if (mBinding.albumPhotographerCheckBox.isChecked()) {
            String personName = (String) mBinding.albumPhotographerSpinner.getSelectedItem();
            Optional<Person> personOptional = getAlbum().getPersons().stream().filter(person -> person.getFirstName().equals(personName)).findFirst();
            personOptional.ifPresent(person -> filterData.put(Filter.BY_PERSON, String.valueOf(person.getId())));
        }

        mAlbumViewModel.filter(filterData);
    }

    private void updateView(StatusWrapper<Pair<Album, List<Image>>> albumStatusWrapper) {
        Pair<Album, List<Image>> pair = albumStatusWrapper.getValue();
        Album album = pair.first;
        List<Image> images = pair.second;

        mBinding.setAlbum(album);
        mBinding.setStatus(albumStatusWrapper.getCode());
        mBinding.setError(getString(albumStatusWrapper.getErrorMessage()));

        if (albumStatusWrapper.getCode() == StatusWrapper.STATUS_LOADED) {
            Objects.requireNonNull(getSupportActionBar()).setTitle(album.getName());

            mAdapterCategory.clear();
            mAdapterCategory.addAll(album.getCategories());
            mAdapterCategory.notifyDataSetChanged();

            mAdapterPhotographer.clear();
            mAdapterPhotographer.addAll(album.getPersons().stream().map(Person::getFirstName).collect(Collectors.toList()));
            mAdapterPhotographer.notifyDataSetChanged();

            mAdapterDate.clear();
            mAdapterDate.addAll(album.getDates().stream().map(date -> MessageFormat.format("{0,date}", date)).collect(Collectors.toList()));
            mAdapterDate.notifyDataSetChanged();

            mImageAdapter.clear();
            mImageAdapter.addAll(images);
            mImageAdapter.notifyDataSetChanged();
        } else if (albumStatusWrapper.getCode() == StatusWrapper.STATUS_ERROR) {
            mImageAdapter.clear();
            Reason reason = albumStatusWrapper.getReason();
            mBinding.setError(getString(reason == Reason.EMPTY ? R.string.album_empty : reason.getStringRes()));
        }

        int hits = mImageAdapter.getImages().size();
        if (hits > 0) {
            mBinding.setHits(getString(R.string.hits, hits));
        } else {
            mBinding.setHits("");
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustColumnCount(newConfig);
    }

    private void adjustColumnCount(@NonNull Configuration configuration) {
        double width = configuration.screenWidthDp;
        int columnCount = Double.valueOf(Math.round(width / 150d)).intValue();

        mBinding.imageContainer.setNumColumns(columnCount);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.album_info) {
            AlbumInfoBottomSheet albumInfoBottomSheet = AlbumInfoBottomSheet.newInstance(getAlbum());
            albumInfoBottomSheet.show(getSupportFragmentManager(), albumInfoBottomSheet.getTag());
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_album, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int id = buttonView.getId();
        if (id == R.id.expand_checkBox) {
            if (isChecked) {
                buttonView.setButtonDrawable(R.drawable.ic_arrow_up_accent_animation);
                ((Animatable) Objects.requireNonNull(buttonView.getButtonDrawable())).start();
                ViewUtils.expand(mBinding.expandable);
            } else {
                buttonView.setButtonDrawable(R.drawable.ic_arrow_down_accent_animation);
                ((Animatable) Objects.requireNonNull(buttonView.getButtonDrawable())).start();
                ViewUtils.collapse(mBinding.expandable);
            }
        } else if (id == R.id.album_photographer_check_box) {
            mBinding.albumPhotographerSpinner.setEnabled(isChecked);
        } else if (id == R.id.album_category_check_box) {
            mBinding.albumCategorySpinner.setEnabled(isChecked);
        } else if (id == R.id.album_date_check_box) {
            mBinding.albumDateSpinner.setEnabled(isChecked);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Image image = mImageAdapter.getItem(position);

        if (image == null) {
            Snackbar.make(mBinding.getRoot(), R.string.image_not_downloaded, Snackbar.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ImageActivity.class);
        intent.putExtra(ImageActivity.GALLERY_IMAGE_KEY, image);
        intent.putParcelableArrayListExtra(ImageActivity.GALLERY_IMAGES_KEY, new ArrayList<>(mImageAdapter.getImages()));
        mImageLauncher.launch(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (handleIntent(intent, this)) {
            super.onNewIntent(intent);
        } else {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public static boolean handleIntent(@NonNull Intent intent, @Nullable GalleryAlbumActivity activity) {
        Album album = intent.getParcelableExtra(GALLERY_ALBUM_KEY);

        // internal intent
        if (album != null) {
            if (activity != null) {
                activity.mAlbum = album;
            }

            return true;
        }

        // external intent via deep link
        if (Intent.ACTION_VIEW.equals(intent.getAction()) || QEDIntent.ACTION_SHOW_ALBUM.equals(intent.getAction())) {
            Uri data = intent.getData();

            if (data != null) {
                String host = data.getHost();
                if (host == null || !host.equals("qedgallery.qed-verein.de")) return false;

                String path = data.getPath();
                if (path == null || !path.startsWith("/album_view.php")) return false;

                String idStr = data.getQueryParameter("albumid");
                if (idStr == null) return false;

                // TODO apply filters
                try {
                    long id = Long.parseLong(idStr);

                    if (activity != null) {
                        Preferences.general().edit().setDrawerSelection(MainActivity.DrawerSelection.GALLERY).apply();
                        activity.mAlbum = new Album(id);
                    }

                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        return false;
    }
}
