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

package rs.ltt.android.database.dao;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rs.ltt.android.entity.KeywordOverwriteEntity;
import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.QueryItemOverwriteEntity;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class OverwriteDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverwriteDao.class);

    @Insert(onConflict = REPLACE)
    public abstract void insert(KeywordOverwriteEntity keywordToggle);

    @Insert(onConflict = REPLACE)
    public abstract void insert(MailboxOverwriteEntity entity);

    @Insert(onConflict = REPLACE)
    public abstract void insert(QueryItemOverwriteEntity queryItemOverwriteEntity);

    @Delete
    public abstract void delete(QueryItemOverwriteEntity queryItemOverwriteEntity);

    @Query("delete from query_item_overwrite where threadId=:threadId and type=:type")
    public abstract int deleteQueryOverwritesByThread(String threadId, QueryItemOverwriteEntity.Type type);

    @Query("delete from mailbox_overwrite where threadId=:threadId")
    protected abstract int deleteMailboxOverwritesByThread(String threadId);

    @Query("delete from keyword_overwrite where threadId=:threadId")
    protected abstract int deleteKeywordOverwritesByThread(String threadId);

    @Transaction
    public void revertKeywordOverwrites(String threadId) {
        int keywordOverwrites = deleteKeywordOverwritesByThread(threadId);
        int queryOverwrites = deleteQueryOverwritesByThread(threadId, QueryItemOverwriteEntity.Type.KEYWORD);
        if (keywordOverwrites > 0 || queryOverwrites > 0) {
            LOGGER.info("Deleted {} keyword overwrites and {} query overwrites for thread {}", keywordOverwrites, queryOverwrites, threadId);
        }
    }

    @Transaction
    public void revertMailboxOverwrites(String threadId) {
        int mailboxOverwrites = deleteMailboxOverwritesByThread(threadId);
        int queryOverwrites = deleteQueryOverwritesByThread(threadId, QueryItemOverwriteEntity.Type.MAILBOX);
        if (mailboxOverwrites > 0 || queryOverwrites > 0) {
            LOGGER.info("Deleted {} mailbox overwrites and {} query overwrites for thread {}", mailboxOverwrites, queryOverwrites, threadId);
        }
    }

    @Query("select * from keyword_overwrite where threadId=:threadId")
    public abstract ListenableFuture<KeywordOverwriteEntity> getKeywordOverwrite(String threadId);

    @Query("select * from mailbox_overwrite where threadId=:threadId")
    public abstract LiveData<List<MailboxOverwriteEntity>> getMailboxOverwrites(String threadId);
}
