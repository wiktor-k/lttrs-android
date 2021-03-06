/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.common.util.concurrent.MoreExecutors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import rs.ltt.android.LttrsNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentThreadBinding;
import rs.ltt.android.entity.ExpandedPosition;
import rs.ltt.android.entity.FullEmail;
import rs.ltt.android.ui.ThreadModifier;
import rs.ltt.android.ui.activity.ComposeActivity;
import rs.ltt.android.ui.adapter.OnComposeActionTriggered;
import rs.ltt.android.ui.adapter.OnFlaggedToggled;
import rs.ltt.android.ui.adapter.ThreadAdapter;
import rs.ltt.android.ui.model.ThreadViewModel;
import rs.ltt.android.ui.model.ThreadViewModelFactory;
import rs.ltt.android.util.Event;

public class ThreadFragment extends AbstractLttrsFragment implements OnFlaggedToggled, OnComposeActionTriggered {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadFragment.class);

    private FragmentThreadBinding binding;
    private ThreadViewModel threadViewModel;
    private ThreadAdapter threadAdapter;

    private ThreadViewModel.MenuConfiguration menuConfiguration = ThreadViewModel.MenuConfiguration.none();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final Bundle bundle = getArguments();
        final ThreadFragmentArgs arguments = ThreadFragmentArgs.fromBundle(bundle == null ? new Bundle() : bundle);
        final String threadId = arguments.getThread();
        final String label = arguments.getLabel();
        final boolean triggerRead = arguments.getTriggerRead();
        final ViewModelProvider viewModelProvider = new ViewModelProvider(
                getViewModelStore(),
                new ThreadViewModelFactory(
                        requireActivity().getApplication(),
                        getLttrsViewModel().getAccount(),
                        threadId,
                        label,
                        triggerRead
                )
        );
        threadViewModel = viewModelProvider.get(ThreadViewModel.class);
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_thread, container, false);

        //do we want a custom layout manager that does *NOT* remember scroll position when more
        //than one item is expanded. with variable sized items this might be annoying

        threadAdapter = new ThreadAdapter(threadViewModel.expandedItems);

        //the default change animation causes UI glitches when expanding or collapsing item
        //for now it's better to just disable it. In the future we may write our own animator
        RecyclerView.ItemAnimator itemAnimator = binding.list.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }

        binding.list.setAdapter(threadAdapter);
        threadViewModel.getEmails().observe(getViewLifecycleOwner(), this::onEmailsChanged);
        threadViewModel.getSubjectWithImportance().observe(getViewLifecycleOwner(), threadAdapter::setSubjectWithImportance);
        threadViewModel.getFlagged().observe(getViewLifecycleOwner(), threadAdapter::setFlagged);
        threadViewModel.getMenuConfiguration().observe(getViewLifecycleOwner(), menuConfiguration -> {
            this.menuConfiguration = menuConfiguration;
            requireActivity().invalidateOptionsMenu();
        });
        threadAdapter.setOnFlaggedToggledListener(this);
        threadAdapter.setOnComposeActionTriggeredListener(this);
        threadViewModel.getThreadViewRedirect().observe(getViewLifecycleOwner(), this::onThreadViewRedirect);
        return binding.getRoot();
    }

    private void onThreadViewRedirect(final Event<String> event) {
        if (event.isConsumable()) {
            final String threadId = event.consume();
            final NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
            navController.navigate(LttrsNavigationDirections.actionToThread(
                    threadId,
                    null,
                    true
            ));
        }
    }

    private void onEmailsChanged(PagedList<FullEmail> fullEmails) {
        if (threadViewModel.jumpedToFirstUnread.compareAndSet(false, true)) {
            threadViewModel.expandedPositions.addListener(() -> {
                try {
                    List<ExpandedPosition> expandedPositions = threadViewModel.expandedPositions.get();
                    threadAdapter.expand(expandedPositions);
                    threadAdapter.submitList(fullEmails, () -> {
                        final int pos = expandedPositions.get(0).position;
                        binding.list.scrollToPosition(pos == 0 ? 0 : pos + 1);
                    });
                } catch (Exception e) {

                }
            }, MoreExecutors.directExecutor());
        } else {
            threadAdapter.submitList(fullEmails);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LOGGER.info("on activity result code={}, result={}, intent={}", requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ComposeActivity.REQUEST_EDIT_DRAFT) {
            if (resultCode == ComposeActivity.RESULT_OK) {
                final UUID uuid = (UUID) data.getSerializableExtra(ComposeActivity.EDITING_TASK_ID_EXTRA);
                final boolean threadDiscarded = data.getBooleanExtra(ComposeActivity.DISCARDED_THREAD_EXTRA, false);
                if (uuid != null) {
                    threadViewModel.waitForEdit(uuid);
                } else if (threadDiscarded) {
                    final NavController navController = Navigation.findNavController(
                            requireActivity(),
                            R.id.nav_host_fragment
                    );
                    navController.popBackStack();
                }

            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_thread, menu);
        menu.findItem(R.id.action_archive).setVisible(menuConfiguration.archive);
        final MenuItem removeLabelItem = menu.findItem(R.id.action_remove_label);
        removeLabelItem.setVisible(menuConfiguration.removeLabel);
        removeLabelItem.setTitle(getString(R.string.remove_label_x, threadViewModel.getLabel()));
        menu.findItem(R.id.action_move_to_inbox).setVisible(menuConfiguration.moveToInbox);
        menu.findItem(R.id.action_move_to_trash).setVisible(menuConfiguration.moveToTrash);
        menu.findItem(R.id.action_mark_important).setVisible(menuConfiguration.markImportant);
        menu.findItem(R.id.action_mark_not_important).setVisible(menuConfiguration.markNotImportant);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {

        final NavController navController = Navigation.findNavController(
                requireActivity(),
                R.id.nav_host_fragment
        );

        switch (menuItem.getItemId()) {
            case R.id.action_mark_unread:
                threadViewModel.markUnread();
                navController.popBackStack();
                return true;
            case R.id.action_archive:
                getThreadModifier().archive(threadViewModel.getThreadId());
                navController.popBackStack();
                return true;
            case R.id.action_remove_label:
                getThreadModifier().removeFromMailbox(
                        threadViewModel.getThreadId(),
                        threadViewModel.getMailbox()
                );
                navController.popBackStack();
                return true;
            case R.id.action_move_to_inbox:
                getThreadModifier().moveToInbox(threadViewModel.getThreadId());
                navController.popBackStack();
                return true;
            case R.id.action_move_to_trash:
                getThreadModifier().moveToTrash(threadViewModel.getThreadId());
                navController.popBackStack();
                return true;
            case R.id.action_mark_important:
                threadViewModel.markImportant();
                return true;
            case R.id.action_mark_not_important:
                //TODO if label == important (coming from important view); pop back stack
                threadViewModel.markNotImportant();
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    @Override
    public void onFlaggedToggled(String threadId, boolean target) {
        threadViewModel.toggleFlagged(threadId, target);
    }

    private ThreadModifier getThreadModifier() {
        final Activity activity = requireActivity();
        if (activity instanceof ThreadModifier) {
            return (ThreadModifier) activity;
        }
        throw new IllegalStateException("Activity does not implement ThreadModifier");
    }

    @Override
    public void onEditDraft(String emailId) {
        ComposeActivity.editDraft(
                this,
                requireAccount().id,
                emailId
        );
    }

    @Override
    public void onReplyAll(String emailId) {
        ComposeActivity.replyAll(
                this,
                requireAccount().id,
                emailId
        );
    }
}
