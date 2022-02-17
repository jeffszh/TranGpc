package misc

func GetWord(data []byte, ind int) uint16 {
	return uint16(data[ind]) | uint16(data[ind+1])<<8
}

//goland:noinspection GoStandardMethods
type SeekableInputOutputStream interface {
	Seek(offset int)
	Eof() bool
	ReadByte() int
	ReadWord() int
	ReadWordAt(offset int) int
}

type seekableByteInputOutputStream struct {
	// 内容数组
	byteArray []byte

	// 流式读写的指针
	pos int
}

func NewSeekableByteInputOutputStream(byteArray []byte) SeekableInputOutputStream {
	return &seekableByteInputOutputStream{
		byteArray, 0,
	}
}

func (stream *seekableByteInputOutputStream) Seek(offset int) {
	stream.pos = offset
}

func (stream *seekableByteInputOutputStream) Eof() bool {
	return stream.pos >= len(stream.byteArray)
}

//goland:noinspection GoStandardMethods
func (stream *seekableByteInputOutputStream) ReadByte() int {
	b := stream.byteArray[stream.pos]
	stream.pos++
	return int(b)
}

func (stream *seekableByteInputOutputStream) ReadWord() int {
	l := stream.ReadByte()
	h := stream.ReadByte()
	return l | h<<8
}

func (stream *seekableByteInputOutputStream) ReadWordAt(offset int) int {
	stream.Seek(offset)
	return stream.ReadWord()
}
