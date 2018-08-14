#ifndef __GZIP_CPP_H__
#define __GZIP_CPP_H__

#include <zlib.h>

#include <list>
#include <memory>
#include <tuple>

namespace gzip {

    struct DataBlock {
        char *ptr;
        std::size_t size;
    };

    typedef std::shared_ptr<DataBlock> Data;
    typedef std::list<Data> DataList;

    Data AllocateData(std::size_t size);
    Data ExpandDataList(const DataList &data_list);

/// Compress processor.
    class Comp {
    public:
        enum class Level {
            Default = -1,
            Min = 0,
            Level_1 = 1,
            Level_2 = 2,
            Level_3 = 3,
            Level_4 = 4,
            Level_5 = 5,
            Level_6 = 6,
            Level_7 = 7,
            Level_8 = 8,
            Max = 9
        };
    public:
        /// Construct a compressor.
        explicit Comp(Level level = Level::Default, bool gzip_header = false);

        /// Destructor, will release z_stream.
        ~Comp();

        /// Returns true if compressor initialize successfully.
        bool IsSucc() const;

        /// Compress incoming buffer to DataBlock list.
        DataList Process(const char *buffer, std::size_t size,
                         int flush = Z_NO_FLUSH);

        /// Compress incoming data to dataBlock list.
        DataList Process(const Data &data, int flush = Z_NO_FLUSH);

    private:
        Level level_;
        z_stream zs_;
        bool init_ok_;
    };

/// Decompress processor.
    class Decomp {
    public:
        /// Construct a decompressor.
        Decomp();

        /// Destructor, will release z_stream.
        ~Decomp();

        /// Returns true if decompressor initialize successfully.
        bool IsSucc() const;

        /// Decompress incoming buffer to DataBlock list.
        std::tuple<bool, DataList> Process(const Data &compressed_data);

    private:
        z_stream zs_;
        bool init_ok_;
    };

}  // namespace gzip

#endif
