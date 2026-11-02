-- Holds the lock long enough for a second server to arrive, before a statement that commits and after it.
SELECT SLEEP(1);
CREATE TABLE lock_test_first (id INT PRIMARY KEY);
SELECT SLEEP(3);
CREATE TABLE lock_test_second (id INT PRIMARY KEY);
