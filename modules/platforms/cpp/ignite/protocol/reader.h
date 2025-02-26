/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <ignite/common/bytes_view.h>
#include <ignite/common/ignite_error.h>
#include <ignite/common/uuid.h>
#include <ignite/protocol/utils.h>

#include <msgpack.h>

#include <cstdint>
#include <functional>

namespace ignite::protocol {

/**
 * Reader.
 */
class reader {
public:
    // Deleted
    reader() = delete;
    reader(reader &&) = delete;
    reader(const reader &) = delete;
    reader &operator=(reader &&) = delete;
    reader &operator=(const reader &) = delete;

    /**
     * Constructor.
     *
     * @param buffer Buffer.
     */
    explicit reader(bytes_view buffer);

    /**
     * Destructor.
     */
    ~reader() { msgpack_unpacked_destroy(&m_current_val); }

    /**
     * Read object of type T from msgpack stream.
     *
     * @tparam T Type of the object to read.
     * @return Object of type T.
     * @throw ignite_error if there is no object of specified type in the stream.
     */
    template<typename T>
    [[nodiscard]] T read_object() {
        check_data_in_stream();

        auto res = unpack_object<T>(m_current_val.data);
        next();

        return res;
    }

    /**
     * Read object of type T from msgpack stream.
     *
     * @tparam T Type of the object to read.
     * @return Object of type T or @c nullopt if there is object of other type in the stream.
     * @throw ignite_error if there is no data left in the stream.
     */
    template<typename T>
    [[nodiscard]] std::optional<T> try_read_object() {
        check_data_in_stream();

        auto res = try_unpack_object<T>(m_current_val.data);
        if (res)
            next();

        return res;
    }

    /**
     * Read object of type T from msgpack stream or nil.
     *
     * @tparam T Type of the object to read.
     * @return Object of type T or std::nullopt if there is nil in the stream.
     * @throw ignite_error if there is no object of specified type in the stream.
     */
    template<typename T>
    [[nodiscard]] std::optional<T> read_object_nullable() {
        if (try_read_nil())
            return std::nullopt;

        return read_object<T>();
    }

    /**
     * Read object of type T from msgpack stream or returns default value if the value in stream is nil.
     *
     * @tparam T Type of the object to read.
     * @param on_nil Object to be returned on nil.
     * @return Object of type T or @c on_nil if there is nil in stream.
     * @throw ignite_error if there is no object of specified type in the stream.
     */
    template<typename T>
    [[nodiscard]] T read_object_or_default(T &&on_nil) {
        if (try_read_nil())
            return std::forward<T>(on_nil);

        return read_object<T>();
    }

    /**
     * Read int16.
     *
     * @return Value.
     */
    [[nodiscard]] std::int16_t read_int16() { return read_object<std::int16_t>(); }

    /**
     * Read int32.
     *
     * @return Value.
     */
    [[nodiscard]] std::int32_t read_int32() { return read_object<std::int32_t>(); }

    /**
     * Read int32 or nullopt.
     *
     * @return Value or nullopt if the next value in stream is not integer.
     */
    [[nodiscard]] std::optional<std::int32_t> try_read_int32() { return try_read_object<std::int32_t>(); }

    /**
     * Read int64 number.
     *
     * @return Value.
     */
    [[nodiscard]] std::int64_t read_int64() { return read_object<int64_t>(); }

    /**
     * Read bool.
     *
     * @return Value.
     */
    [[nodiscard]] bool read_bool() { return read_object<bool>(); }

    /**
     * Read string.
     *
     * @return String value.
     */
    [[nodiscard]] std::string read_string() { return read_object<std::string>(); }

    /**
     * Read string.
     *
     * @return String value or nullopt.
     */
    [[nodiscard]] std::optional<std::string> read_string_nullable() { return read_object_nullable<std::string>(); }

    /**
     * Read UUID.
     *
     * @return UUID value.
     */
    [[nodiscard]] uuid read_uuid() { return read_object<uuid>(); }

    /**
     * Read Map size.
     *
     * @return Map size.
     */
    [[nodiscard]] uint32_t read_map_size() const {
        check_data_in_stream();

        if (m_current_val.data.type != MSGPACK_OBJECT_MAP)
            throw ignite_error("The value in stream is not a Map");

        return m_current_val.data.via.map.size;
    }

    /**
     * Read Map raw.
     *
     * @param handler Pair handler.
     */
    void read_map_raw(const std::function<void(const msgpack_object_kv &)> &handler) {
        auto size = read_map_size();
        for (std::uint32_t i = 0; i < size; ++i) {
            handler(m_current_val.data.via.map.ptr[i]);
        }
        next();
    }

    /**
     * Read Map.
     *
     * @tparam K Key type.
     * @tparam V Value type.
     * @param handler Pair handler.
     */
    template<typename K, typename V>
    void read_map(const std::function<void(K &&, V &&)> &handler) {
        auto size = read_map_size();
        for (std::uint32_t i = 0; i < size; ++i) {
            auto key = unpack_object<K>(m_current_val.data.via.map.ptr[i].key);
            auto val = unpack_object<V>(m_current_val.data.via.map.ptr[i].val);
            handler(std::move(key), std::move(val));
        }
        next();
    }

    /**
     * Read array size.
     *
     * @return Array size.
     */
    [[nodiscard]] uint32_t read_array_size() const {
        check_data_in_stream();

        return unpack_array_size(m_current_val.data);
    }

    /**
     * Read array.
     *
     * @param read_func Object read function.
     */
    void read_array_raw(const std::function<void(std::uint32_t idx, const msgpack_object &)> &read_func) {
        auto size = read_array_size();
        for (std::uint32_t i = 0; i < size; ++i) {
            read_func(i, m_current_val.data.via.array.ptr[i]);
        }
        next();
    }

    /**
     * Read array.
     *
     * @tparam T Value type.
     * @param unpack_func Object unpack function.
     */
    template<typename T>
    [[nodiscard]] std::vector<T> read_array(const std::function<T(const msgpack_object &)> &unpack_func) {
        auto size = read_array_size();
        std::vector<T> res;
        res.reserve(size);
        for (std::uint32_t i = 0; i < size; ++i) {
            auto val = unpack_func(m_current_val.data.via.array.ptr[i]);
            res.emplace_back(std::move(val));
        }
        next();
        return std::move(res);
    }

    /**
     * Read array.
     *
     * @tparam T Value type.
     * @param handler Value handler.
     */
    template<typename T>
    [[nodiscard]] std::vector<T> read_array() {
        return read_array<T>(unpack_object<T>);
    }

    /**
     * Read array.
     *
     * @return Binary data view.
     */
    [[nodiscard]] bytes_view read_binary() {
        auto res = unpack_binary(m_current_val.data);
        next();
        return res;
    }

    /**
     * If the next value is Nil, read it and move reader to the next position.
     *
     * @return @c true if the value was nil.
     */
    bool try_read_nil();

    /**
     * Skip next value.
     */
    void skip() { next(); }

    /**
     * Position.
     *
     * @return Current position in memory.
     */
    [[nodiscard]] size_t position() const { return m_offset; }

private:
    /**
     * Move to the next value.
     */
    void next();

    /**
     * Check whether there is a data in stream and throw ignite_error if there is none.
     */
    void check_data_in_stream() const {
        if (m_move_res < 0)
            throw ignite_error("No more data in stream");
    }

    /** Buffer. */
    bytes_view m_buffer;

    /** Current value. */
    msgpack_unpacked m_current_val;

    /** Result of the last move operation. */
    msgpack_unpack_return m_move_res;

    /** Offset to next value. */
    size_t m_offset_next{0};

    /** Offset. */
    size_t m_offset{0};
};

} // namespace ignite::protocol
