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

package rs.ltt.android.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

import rs.ltt.android.entity.IdentityWithNameAndEmail;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.mua.Status;

public class SaveDraftWorker extends AbstractCreateEmailWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCreateEmailWorker.class);

    public SaveDraftWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        final IdentityWithNameAndEmail identity = getIdentity();
        final Email email = buildEmail(identity);
        try {
            final String emailId = getMua().draft(email).get();
            refresh();
            final String threadId = getDatabase().threadAndEmailDao().getThreadId(emailId);
            LOGGER.info("Email saved as draft with id {} in thread {}", emailId, threadId);
            final Data data = new Data.Builder()
                    .putString("emailId", emailId)
                    .putString("threadId", threadId)
                    .build();
            return Result.success(data);
        } catch (ExecutionException e) {
            LOGGER.warn("Unable to safe email as draft", e);
            return Result.failure();
        } catch (InterruptedException e) {
            return Result.retry();
        }
    }
}
