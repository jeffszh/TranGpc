package misc

func GetWord(data []byte, ind int) uint16 {
	return uint16(data[ind]) | uint16(data[ind+1])<<8
}
