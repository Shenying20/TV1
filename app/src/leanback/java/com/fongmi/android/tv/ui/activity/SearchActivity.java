package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.TitlePresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchActivity extends BaseActivity implements VodPresenter.OnClickListener {

    private ActivitySearchBinding mBinding;
    private SiteViewModel mSiteViewModel;
    private ArrayObjectAdapter mAdapter;
    private ExecutorService mService;
    private List<Site> mSites;

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkKeyword();
    }

    @Override
    protected void initView() {
        CustomKeyboard.init(mBinding);
        mBinding.keyword.requestFocus();
        setRecyclerView();
        setViewModel();
        checkKeyword();
        setSite();
    }

    @Override
    protected void initEvent() {
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.clear.setOnClickListener(view -> mBinding.keyword.setText(""));
        mBinding.remote.setOnClickListener(view -> PushActivity.start(this));
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) mBinding.search.performClick();
            return true;
        });
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(String.class, new TitlePresenter());
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
    }

    private void setViewModel() {
        mSiteViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSiteViewModel.result.observe(this, result -> {
            if (mService != null) addVideo(result);
        });
    }

    private void setSite() {
        mSites = new ArrayList<>();
        for (Site site : ApiConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        if (!mSites.contains(ApiConfig.get().getHome())) return;
        mSites.remove(ApiConfig.get().getHome());
        mSites.add(0, ApiConfig.get().getHome());
    }

    private void checkKeyword() {
        if (getKeyword().isEmpty()) return;
        stopSearch();
        mAdapter.clear();
        mBinding.keyword.setText(getKeyword());
        mBinding.keyword.setSelection(mBinding.keyword.length());
        new Handler().postDelayed(this::onSearch, 250);
    }

    private void addVideo(Result result) {
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> items : Lists.partition(result.getList(), 5)) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(new VodPresenter(this, 5));
            adapter.addAll(0, items);
            rows.add(new ListRow(adapter));
        }
        mAdapter.add(result.getList().get(0).getSite().getName());
        mAdapter.addAll(mAdapter.size(), rows);
        mBinding.progressLayout.showContent();
    }

    private void onSearch() {
        String keyword = mBinding.keyword.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) return;
        mService = Executors.newFixedThreadPool(5);
        for (Site site : mSites) mService.execute(() -> mSiteViewModel.searchContent(site.getKey(), keyword));
        showProgress();
    }

    private void stopSearch() {
        if (mService == null) return;
        mService.shutdownNow();
        mService = null;
    }

    private void showProgress() {
        mBinding.layout.setVisibility(View.GONE);
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        mBinding.progressLayout.showProgress();
    }

    private void hideProgress() {
        mBinding.layout.setVisibility(View.VISIBLE);
        mBinding.progressLayout.setVisibility(View.INVISIBLE);
    }

    private boolean isProgressVisible() {
        return mBinding.progressLayout.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onItemClick(Vod item) {
        DetailActivity.start(this, item.getSite().getKey(), item.getVodId());
    }

    @Override
    public void onBackPressed() {
        if (isProgressVisible()) {
            mAdapter.clear();
            hideProgress();
            stopSearch();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearch();
    }
}
