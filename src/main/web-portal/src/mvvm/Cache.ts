import {ViewModel} from "./ViewModel";

const cache: { [key: string]: Cache } = {};

export class Cache {

    static of(socketAddress: string) {
        if (!cache[socketAddress])
            cache[socketAddress] = new Cache(socketAddress);

        return cache[socketAddress];
    }

    private constructor(private socketAddress: string) {
        this.socketAddress = socketAddress;
    }

    readonly propertyObservers: { [key: string]: (prop: any) => void } = {};
    readonly viewModelObservers: { [key: string]: (vm: ViewModel) => void } = {};
    readonly methodObservers: { [key: string]: any[] } = {};
    readonly viewModelCache: { [key: string]: ViewModel } = {};

}