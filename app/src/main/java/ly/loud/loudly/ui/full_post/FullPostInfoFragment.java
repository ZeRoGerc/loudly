package ly.loud.loudly.ui.full_post;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.hannesdorfmann.fragmentargs.FragmentArgs;
import com.hannesdorfmann.fragmentargs.annotation.Arg;
import com.hannesdorfmann.fragmentargs.annotation.FragmentWithArgs;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import ly.loud.loudly.R;
import ly.loud.loudly.application.Loudly;
import ly.loud.loudly.application.models.GetterModel;
import ly.loud.loudly.base.entities.Person;
import ly.loud.loudly.base.plain.PlainPost;
import ly.loud.loudly.base.single.Comment;
import ly.loud.loudly.networks.Networks;
import ly.loud.loudly.ui.TitledFragment;
import ly.loud.loudly.ui.adapters.FullPostInfoAdapter;
import ly.loud.loudly.ui.adapters.FullPostInfoAdapter.FullPostInfoClickListener;
import ly.loud.loudly.ui.people_list.PeopleListFragment;
import ly.loud.loudly.util.Utils;
import solid.collections.SolidList;

import static ly.loud.loudly.application.Loudly.getApplication;
import static ly.loud.loudly.application.models.GetterModel.LIKES;
import static ly.loud.loudly.application.models.GetterModel.SHARES;
import static ly.loud.loudly.util.ListUtils.asArrayList;
import static ly.loud.loudly.util.Utils.launchCustomTabs;

@FragmentWithArgs
public class FullPostInfoFragment extends TitledFragment
        implements FullPostInfoView, FullPostInfoClickListener {

    @SuppressWarnings("NullableProblems") // Butterknife
    @BindView(R.id.full_post_info_layout_recycler)
    @NonNull
    RecyclerView recyclerView;

    @SuppressWarnings("NullableProblems") // Inject
    @Inject
    @NonNull
    GetterModel getterModel;

    @SuppressWarnings("NullableProblems") // Inject
    @Inject
    @NonNull
    FullPostInfoPresenter presenter;

    @SuppressWarnings("NullableProblems") // Arg
    @Arg
    @NonNull
    PlainPost post;

    @SuppressWarnings("NullableProblems") // onViewCreated
    @NonNull
    private FullPostInfoAdapter fullPostInfoAdapter;

    @SuppressWarnings("NullableProblems") // onViewCreated
    @NonNull
    private Unbinder unbinder;

    public static FullPostInfoFragment newInstance(@NonNull PlainPost post) {
        return new FullPostInfoFragmentBuilder(post).build();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getApplication(getContext()).getAppComponent().plus(new FullPostInfoModule()).inject(this);
        FragmentArgs.inject(this);
        setHasOptionsMenu(true);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    @Nullable
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.full_post_info_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        unbinder = ButterKnife.bind(this, view);
        presenter.onBindView(this);

        fullPostInfoAdapter = new FullPostInfoAdapter(post);
        fullPostInfoAdapter.setFullPostInfoClickListener(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(fullPostInfoAdapter);
        presenter.loadComments(post);
    }

    @Override
    public void onDestroyView() {
        presenter.onUnbindView(this);
        unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    @NonNull
    public String getDefaultTitle() {
        return String.format(
                getString(R.string.full_post_info_title),
                getString(Utils.getNetworkTitleResourceByPost(post))
        );
    }

    @Override
    public void onNewCommentsFromNetwork(@NonNull SolidList<Comment> comments, @Networks.Network int network) {
        fullPostInfoAdapter.addComments(comments, network);
    }

    @Override
    public void onGotWebPageUrl(@NonNull String url) {
        launchCustomTabs(url, getActivity());
    }

    @Override
    public void onError(@StringRes int errorRes) {
        Toast.makeText(getContext(), errorRes, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPostSharesClick(@NonNull PlainPost post) {
        PeopleListFragment fragment = PeopleListFragment.newInstance(
                Utils.getInstances(post),
                SHARES
        );
        fragment.show(getFragmentManager(), fragment.getTag());
    }

    @Override
    public void onPostLikesClick(@NonNull PlainPost post) {
        PeopleListFragment fragment = PeopleListFragment.newInstance(
                Utils.getInstances(post),
                LIKES
        );
        fragment.show(getFragmentManager(), fragment.getTag());
    }

    @Override
    public void onCommentLikesClick(@NonNull Comment comment) {
        PeopleListFragment fragment = PeopleListFragment.newInstance(
                asArrayList(comment),
                LIKES
        );
        fragment.show(getFragmentManager(), fragment.getTag());
    }

    @Override
    public void onPhotoClick(@NonNull Person person) {
        presenter.getUserPageUrl(person);
    }

    @Override
    public void onCommentClick(@NonNull Comment comment) {
        presenter.getCommentPageUrl(comment, post);
    }

    @Module
    public static class FullPostInfoModule {
        @Provides
        @NonNull
        public FullPostInfoPresenter provideFullPostInfoPresenter(
                @NonNull Loudly loudlyApp,
                @NonNull GetterModel getterModel
        ) {
            return new FullPostInfoPresenter(loudlyApp, getterModel);
        }
    }

    @Subcomponent(modules = FullPostInfoModule.class)
    public interface FullPostInfoComponent {

        void inject(@NonNull FullPostInfoFragment fragment);
    }
}
